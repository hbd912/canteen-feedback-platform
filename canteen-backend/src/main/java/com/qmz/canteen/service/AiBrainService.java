package com.qmz.canteen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.qmz.canteen.agent.AiResult;
import com.qmz.canteen.agent.ModelRole;
import com.qmz.canteen.agent.MultiModelRouter;
import com.qmz.canteen.dto.AiDecision;
import com.qmz.canteen.entity.AiEventLog;
import com.qmz.canteen.mapper.AiEventLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 大脑：先规则、后模型。
 * <p>
 * 高频且确定的事件（客服消息、退款、报错、商家留言）直接规则路由，不调用模型；
 * 只有规则判断不了的模糊事件才走 LLM，避免把免费 token 烧在能写 if 的地方。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiBrainService {

    private final MultiModelRouter router;
    private final AiEventLogMapper logMapper;

    // 模型习惯输出 assign_to / need_human，这里统一按下划线映射，避免大小写不匹配
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    public static final String ROLE_CS = "CUSTOMER_SERVICE";
    public static final String ROLE_MERCHANT = "MERCHANT";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_CODE = "CODE_OPS";
    public static final String ROLE_HUMAN = "HUMAN";

    private static final Set<String> ALLOWED = Set.of(ROLE_CS, ROLE_MERCHANT, ROLE_ADMIN, ROLE_CODE, ROLE_HUMAN);

    /**
     * 对一个事件做路由决策，并写入事件日志。
     *
     * @param eventType 事件类型
     * @param source    来源 STUDENT/MERCHANT/ADMIN/SYSTEM
     * @param sourceId  来源业务ID（可空）
     * @param userId    学生/操作人ID（可空）
     * @param stallId   档口ID（可空）
     * @param content   事件内容
     */
    public AiDecision decide(String eventType, String source, Long sourceId,
                             Long userId, Long stallId, String content) {
        long start = System.currentTimeMillis();
        AiResult ai = null;
        AiDecision decision;

        String ruleRole = ruleRoute(eventType, content);
        if (ruleRole != null) {
            decision = new AiDecision();
            decision.setAssignTo(ruleRole);
            decision.setNeedHuman(ROLE_HUMAN.equals(ruleRole));
            decision.setReason("规则命中：" + eventType);
            decision.setAction("按规则直接路由");
        } else {
            try {
                String system = "你是校园食堂的 AI 任务调度大脑。只输出一个 JSON 对象，不要任何解释。"
                        + "字段：assign_to（只能是 CUSTOMER_SERVICE/MERCHANT/ADMIN/CODE_OPS/HUMAN 之一）、"
                        + "reason（不超过 40 字）、need_human（布尔）、action（不超过 30 字）。"
                        + "reason 和 action 必须用简体中文，不要用英文。"
                        + "涉及退款、补偿、改价、发券等金钱操作，assign_to 必须是 HUMAN。";
                String user = "事件类型：" + safe(eventType, 40)
                        + "\n来源：" + safe(source, 20)
                        + "\n内容：" + safe(content, 800);
                ai = router.complete(ModelRole.BRAIN, system, user, 200);
                decision = parseDecision(ai.content());
            } catch (Exception e) {
                log.warn("大脑模型调用失败，按事件类型兜底：{}", e.getMessage());
                decision = ai == null ? fallbackDecision(eventType) : formatFallbackDecision();
            }
        }

        int cost = (int) (System.currentTimeMillis() - start);
        AiEventLog event = new AiEventLog();
        event.setEventType(safe(eventType, 40));
        event.setSource(safe(source, 20));
        event.setSourceId(sourceId);
        event.setUserId(userId);
        event.setStallId(stallId);
        event.setContent(safe(content, 1900));
        event.setDecisionJson(toJson(decision));
        event.setAssignTo(decision.getAssignTo());
        event.setNeedHuman(Boolean.TRUE.equals(decision.getNeedHuman()) ? 1 : 0);
        event.setStatus(Boolean.TRUE.equals(decision.getNeedHuman()) ? "ESCALATED" : "DONE");
        event.setResult(safe(decision.getAction(), 500));
        event.setModelName(ai == null ? "RULE" : ai.model());
        event.setTokens(ai == null ? 0 : ai.totalTokens());
        event.setCostMs(cost);
        logMapper.insert(event);
        return decision;
    }

    /** 商家看本档口日志，管理员看全部 */
    public List<AiEventLog> listLogs(Long stallId, int limit) {
        int size = Math.min(Math.max(limit, 1), 200);
        LambdaQueryWrapper<AiEventLog> query = new LambdaQueryWrapper<>();
        if (stallId != null) {
            query.eq(AiEventLog::getStallId, stallId);
        }
        query.orderByDesc(AiEventLog::getId).last("LIMIT " + size);
        return logMapper.selectList(query);
    }

    /** 规则优先：能写 if 的，绝不调模型 */
    private String ruleRoute(String eventType, String content) {
        if (eventType == null) {
            return null;
        }
        String type = eventType.toUpperCase();
        if (type.contains("REFUND") || type.contains("COMPENSATION") || type.contains("退款") || type.contains("补偿")) {
            return ROLE_HUMAN;
        }
        if (type.contains("MERCHANT_MESSAGE") || type.contains("商家留言")) {
            return ROLE_ADMIN;
        }
        if (type.contains("DISH_IMAGE") || type.contains("DISH_DRAFT") || type.contains("菜品图片")) {
            return ROLE_MERCHANT;
        }
        if (type.contains("ERROR") || type.contains("CODE") || type.contains("报错")) {
            return ROLE_CODE;
        }
        if (type.contains("CS_MESSAGE") || type.contains("客服")) {
            return ROLE_CS;
        }
        if (type.contains("REVIEW") || type.contains("评价")) {
            return ROLE_MERCHANT;
        }
        return null;
    }

    private AiDecision parseDecision(String raw) {
        String json = extractJson(raw);
        if (json == null) {
            throw new IllegalStateException("模型未返回 JSON");
        }
        AiDecision decision;
        try {
            decision = MAPPER.readValue(json, AiDecision.class);
        } catch (Exception e) {
            throw new IllegalStateException("决策 JSON 解析失败：" + e.getMessage(), e);
        }
        if (decision.getAssignTo() == null || !ALLOWED.contains(decision.getAssignTo())) {
            decision.setAssignTo(ROLE_HUMAN);
            decision.setNeedHuman(true);
            decision.setReason("模型返回未知角色，已升级人工");
        }
        if (decision.getNeedHuman() == null) {
            decision.setNeedHuman(ROLE_HUMAN.equals(decision.getAssignTo()));
        }
        // 金钱相关无论如何都要人工
        if (ROLE_HUMAN.equals(decision.getAssignTo())) {
            decision.setNeedHuman(true);
        }
        return decision;
    }

    private AiDecision fallbackDecision(String eventType) {
        AiDecision decision = new AiDecision();
        decision.setAssignTo(ROLE_HUMAN);
        decision.setNeedHuman(true);
        decision.setReason("模型不可用，保守升级人工");
        decision.setAction("进入人工队列");
        return decision;
    }

    /** 模型有返回但格式不对：明确标注为格式问题，方便排查而不是误判成宕机 */
    private AiDecision formatFallbackDecision() {
        AiDecision decision = new AiDecision();
        decision.setAssignTo(ROLE_HUMAN);
        decision.setNeedHuman(true);
        decision.setReason("模型返回格式异常，已升级人工");
        decision.setAction("进入人工队列");
        return decision;
    }

    private static String extractJson(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return raw.substring(start, end + 1);
    }

    private static String toJson(AiDecision decision) {
        try {
            return MAPPER.writeValueAsString(decision);
        } catch (Exception e) {
            return null;
        }
    }

    private static String safe(String text, int max) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
