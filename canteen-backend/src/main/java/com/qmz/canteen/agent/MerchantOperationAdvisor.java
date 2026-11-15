package com.qmz.canteen.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qmz.canteen.entity.Dish;
import com.qmz.canteen.entity.MerchantOperationAction;
import com.qmz.canteen.mapper.DishMapper;
import com.qmz.canteen.mapper.DishReviewMapper;
import com.qmz.canteen.mapper.MerchantOperationActionMapper;
import com.qmz.canteen.mapper.OrderMapper;
import com.qmz.canteen.mapper.StallMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 经营托管顾问：基于「真实订单销量 + 已发布评价」识别经营薄弱菜品，
 * 生成下架建议并持久化为待审批动作；LLM 仅负责生成人类可读的诊断总结，
 * 候选菜的判定由确定性规则完成（保证可靠、可解释，且模型不可用时仍可用）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantOperationAdvisor {

    private final DishMapper dishMapper;
    private final OrderMapper orderMapper;
    private final DishReviewMapper dishReviewMapper;
    private final StallMapper stallMapper;
    private final MerchantOperationActionMapper actionMapper;
    private final AgentProperties props;
    private final LlmClient llmClient;

    /** 近 N 天零销量、或评分/差评表现差的在售菜品，判定为可下架候选 */
    @Transactional
    public List<MerchantOperationAction> diagnose(Long stallId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getStallId, stallId)
                .eq(Dish::getDeleted, 0));

        // 真实销量（已支付且未取消）
        Map<Long, long[]> sales = new HashMap<>();
        for (Map<String, Object> row : orderMapper.aggregateStallDishSales(stallId, since)) {
            Long dishId = toLong(row.get("dishId"));
            long qty = toLong(row.get("quantity"));
            sales.put(dishId, new long[]{qty});
        }

        // 评价统计
        Map<Long, long[]> reviewStats = new HashMap<>();
        Map<Long, Double> avgRating = new HashMap<>();
        for (Map<String, Object> row : dishReviewMapper.aggregateStallReviewStats(stallId)) {
            Long dishId = toLong(row.get("dishId"));
            long reviewCount = toLong(row.get("reviewCount"));
            long badCount = toLong(row.get("badCount"));
            reviewStats.put(dishId, new long[]{reviewCount, badCount});
            Object avg = row.get("avgRating");
            avgRating.put(dishId, avg == null ? 0.0 : ((Number) avg).doubleValue());
        }

        List<MerchantOperationAction> drafts = new ArrayList<>();
        for (Dish dish : dishes) {
            if (!Integer.valueOf(1).equals(dish.getStatus())) {
                continue;
            }
            long qty = sales.containsKey(dish.getId()) ? sales.get(dish.getId())[0] : 0L;
            long reviewCount = reviewStats.containsKey(dish.getId()) ? reviewStats.get(dish.getId())[0] : 0L;
            long badCount = reviewStats.containsKey(dish.getId()) ? reviewStats.get(dish.getId())[1] : 0L;
            double avg = avgRating.getOrDefault(dish.getId(), 0.0);

            boolean weak = qty == 0
                    || (reviewCount >= 3 && avg <= 2.5)
                    || (reviewCount >= 1 && badCount * 2 >= reviewCount);

            if (!weak) {
                continue;
            }
            MerchantOperationAction action = new MerchantOperationAction();
            action.setStallId(stallId);
            action.setActionType(MerchantOperationAction.ACTION_OFF_SHELF);
            action.setDishId(dish.getId());
            action.setDishName(dish.getName());
            action.setExpectedStatus(dish.getStatus());
            action.setTargetStatus(0);
            action.setReason(buildReason(qty, reviewCount, badCount, avg, days));
            action.setEvidenceJson("{\"salesQty\":" + qty + ",\"reviewCount\":" + reviewCount
                    + ",\"badCount\":" + badCount + ",\"avgRating\":" + avg + "}");
            action.setStatus(MerchantOperationAction.STATUS_PENDING);
            action.setModelName(props.getModel());
            action.setCreatedBy("AI");
            drafts.add(action);
        }

        // 清掉本档口旧的待审批建议，避免重复堆积
        actionMapper.delete(new LambdaQueryWrapper<MerchantOperationAction>()
                .eq(MerchantOperationAction::getStallId, stallId)
                .eq(MerchantOperationAction::getStatus, MerchantOperationAction.STATUS_PENDING));
        for (MerchantOperationAction draft : drafts) {
            actionMapper.insert(draft);
        }
        return drafts;
    }

    /** 生成人类可读的诊断总结；LLM 失败时降级为确定性总结 */
    public String summarize(String stallName, int days, int totalDishes, int weakCount,
                            List<MerchantOperationAction> actions) {
        if (actions.isEmpty()) {
            return "近 " + days + " 天「" + stallName + "」经营良好，没有发现明显滞销或口碑差的菜品，继续保持！";
        }
        List<String> lines = new ArrayList<>();
        for (MerchantOperationAction a : actions) {
            lines.add(a.getDishName() + "（" + a.getReason() + "）");
        }
        String fallback = "近 " + days + " 天共扫描 " + totalDishes + " 道在售菜品，识别出 " + weakCount
                + " 道值得关注：\n" + String.join("；", lines)
                + "。\n建议：对这些菜品下架或调整口味，可点击「一键采纳」执行（重要操作请人工确认）。";
        try {
            return llmSummary(stallName, days, totalDishes, weakCount, lines, fallback);
        } catch (Exception e) {
            log.warn("经营诊断总结生成失败，降级为确定性总结：{}", e.getMessage());
            return fallback;
        }
    }

    private String llmSummary(String stallName, int days, int totalDishes, int weakCount,
                              List<String> lines, String fallback) {
        if (!props.isEnabled()) {
            return fallback;
        }
        String system = "你是校园食堂「" + stallName + "」的经营顾问。请根据真实经营数据，用老板能看懂的、务实的中文写一段经营诊断总结，"
                + "指出问题并给出 1-2 条可执行建议。语气专业、简洁，不超过 200 字。不要编造数据，只输出总结正文。";
        String user = "近 " + days + " 天在售菜品 " + totalDishes + " 道，其中 " + weakCount + " 道存在滞销或口碑问题："
                + String.join("；", lines) + "。";
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        messages.add(Map.of("role", "user", "content", user));

        LlmProtocol.ChatRequest req = new LlmProtocol.ChatRequest();
        req.setModel(props.getModel());
        req.setMessages(messages);
        req.setTemperature(0.7);
        req.setMaxTokens(400);

        LlmProtocol.ChatResponse resp = llmClient.chat(req);
        String content = resp.getChoices().get(0).getMessage().getContent();
        if (content == null || content.isBlank()) {
            return fallback;
        }
        return AgentOrchestrator.sanitize(content);
    }

    private String buildReason(long qty, long reviewCount, long badCount, double avg, int days) {
        StringBuilder sb = new StringBuilder();
        if (qty == 0) {
            sb.append("近 ").append(days).append(" 天零销量");
        }
        if (reviewCount >= 3 && avg <= 2.5) {
            if (sb.length() > 0) sb.append("，");
            sb.append("平均评分仅 ").append(avg);
        }
        if (reviewCount >= 1 && badCount * 2 >= reviewCount) {
            if (sb.length() > 0) sb.append("，");
            sb.append(badCount).append(" 条差评占比较高");
        }
        return sb.toString();
    }

    private static long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
}
