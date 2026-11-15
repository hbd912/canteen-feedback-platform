package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qmz.canteen.agent.AgentProperties;
import com.qmz.canteen.agent.AiResult;
import com.qmz.canteen.agent.ModelRole;
import com.qmz.canteen.agent.MultiModelRouter;
import com.qmz.canteen.agent.PromptProperties;
import com.qmz.canteen.common.enums.FeedbackStatus;
import com.qmz.canteen.entity.Dish;
import com.qmz.canteen.entity.DishReview;
import com.qmz.canteen.entity.Feedback;
import com.qmz.canteen.entity.MerchantMessage;
import com.qmz.canteen.mapper.DishMapper;
import com.qmz.canteen.mapper.DishReviewMapper;
import com.qmz.canteen.mapper.FeedbackMapper;
import com.qmz.canteen.mapper.MerchantMessageMapper;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.DishAuditService;
import com.qmz.canteen.vo.AdminAiFindingVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 工作台值班助理的「自动处理」执行器。
 * <p>
 * 处理范围：普通学生留言/商家留言代回复、低分评价标记已处理、菜品上架 AI 审核（通过/驳回）。
 * 退款涉及金钱一律不代执行，留给人工。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAutoHandleService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FeedbackMapper feedbackMapper;
    private final MerchantMessageMapper merchantMessageMapper;
    private final DishReviewMapper dishReviewMapper;
    private final DishMapper dishMapper;
    private final DishAuditService dishAuditService;
    private final com.qmz.canteen.service.NotificationService notificationService;
    private final MultiModelRouter router;
    private final AgentProperties agentProperties;
    private final PromptProperties prompts;

    /** 处理结果：是否处理、做了什么、回复内容、处理条数 */
    public record HandleOutcome(boolean handled, String action, String reply, int count) {
        public static HandleOutcome none() {
            return new HandleOutcome(false, null, null, 0);
        }
    }

    /**
     * 对一条研判自动处理（按聚类下的全部原始 ID 批量回写）。
     */
    @Transactional
    public HandleOutcome autoHandle(AdminAiFindingVO finding) {
        if (finding == null || finding.getSourceId() == null) {
            return HandleOutcome.none();
        }
        if ("P0".equals(finding.getSeverity())) {
            return HandleOutcome.none();
        }
        boolean sensitiveCategory = "FOOD_SAFETY".equals(finding.getCategory())
                || "HYGIENE".equals(finding.getCategory())
                || "REFUND".equals(finding.getCategory());
        if (sensitiveCategory) {
            return HandleOutcome.none();
        }
        return doHandle(finding);
    }

    /**
     * 管理员点「全部处理」= 已明确授权：连 P0 敏感项也代回复并标记已处理，
     * 只有退款（涉及金钱）不代执行，留给人工。
     */
    @Transactional
    public HandleOutcome autoHandleForce(AdminAiFindingVO finding) {
        if (finding == null || finding.getSourceId() == null) {
            return HandleOutcome.none();
        }
        // 退款涉及金钱，只记录不代执行
        if ("REFUND".equals(finding.getCategory()) || "REFUND".equals(finding.getSourceType())) {
            return HandleOutcome.none();
        }
        return doHandle(finding);
    }

    private HandleOutcome doHandle(AdminAiFindingVO finding) {
        String reply = finding.getSuggestedReply();
        boolean hasReply = reply != null && !reply.isBlank();
        List<Long> ids = ids(finding);

        switch (finding.getSourceType()) {
            case "FEEDBACK" -> {
                int updated = 0;
                for (Long id : ids) {
                    Feedback feedback = feedbackMapper.selectById(id);
                    if (feedback == null || !"PENDING".equals(feedback.getStatus())
                            && !"PROCESSING".equals(feedback.getStatus())) {
                        continue;
                    }
                    if (hasReply) {
                        feedback.setReplyContent(reply);
                        feedback.setReplyTime(LocalDateTime.now());
                    }
                    feedback.setStatus(FeedbackStatus.RESOLVED.name());
                    feedbackMapper.updateById(feedback);
                    // 处理完通知学生（通知类，无需回复）
                    if (feedback.getUserId() != null) {
                        notificationService.notify(feedback.getUserId(),
                                com.qmz.canteen.entity.Notification.TYPE_REPLY,
                                "你的留言「" + (feedback.getTitle() == null ? "" : feedback.getTitle()) + "」已处理"
                                        + (hasReply ? "，回复：" + reply : ""),
                                null, feedback.getId());
                    }
                    updated++;
                }
                return new HandleOutcome(updated > 0,
                        hasReply ? "已回复并标记处理" : "已标记处理", hasReply ? reply : null, updated);
            }
            case "MERCHANT_MESSAGE" -> {
                int updated = 0;
                for (Long id : ids) {
                    MerchantMessage message = merchantMessageMapper.selectById(id);
                    if (message == null || message.getStatus() == null || message.getStatus() != 0) {
                        continue;
                    }
                    if (hasReply) {
                        message.setReply(reply);
                        message.setReplyTime(LocalDateTime.now());
                    }
                    message.setStatus(2);
                    merchantMessageMapper.updateById(message);
                    // 关键：把回复作为一条真实的 OUT 消息写入线程，
                    // 商家「往来记录」才能看到管理员/AI 的回复气泡。
                    if (hasReply) {
                        MerchantMessage out = new MerchantMessage();
                        out.setStallId(message.getStallId());
                        out.setUserId(UserContext.getUserId());
                        out.setDirection(MerchantMessage.DIR_OUT);
                        out.setAuthorRole(MerchantMessage.AUTHOR_AI);
                        out.setNotice(0);
                        out.setContent(reply);
                        out.setStatus(0); // 商家侧未读
                        merchantMessageMapper.insert(out);
                    }
                    // 处理完通知对应商家（通知类，无需回复）。标题为空时用内容摘要兜底
                    if (message.getStallId() != null) {
                        String label = message.getTitle() != null && !message.getTitle().isBlank()
                                ? message.getTitle()
                                : (message.getContent() == null ? "留言"
                                : (message.getContent().length() > 16
                                ? message.getContent().substring(0, 16) + "…" : message.getContent()));
                        notificationService.notifyMerchant(message.getStallId(),
                                com.qmz.canteen.entity.Notification.TYPE_ADMIN_NOTICE,
                                "你的留言「" + label + "」已处理"
                                        + (hasReply ? "，回复：" + reply : ""),
                                "/merchant/messages", message.getId());
                    }
                    updated++;
                }
                return new HandleOutcome(updated > 0,
                        hasReply ? "已回复并标记处理" : "已标记处理", hasReply ? reply : null, updated);
            }
            case "REVIEW" -> {
                // 低分评价：助手不代写商家回复，标记「已处理」让其退出待处理列表
                int updated = dishReviewMapper.update(null, new LambdaUpdateWrapper<DishReview>()
                        .in(DishReview::getId, ids)
                        .eq(DishReview::getStatus, DishReview.STATUS_PUBLISHED)
                        .eq(DishReview::getHandled, 0)
                        .set(DishReview::getHandled, 1));
                return new HandleOutcome(updated > 0, "已标记已处理", null, updated);
            }
            case "DISH_AUDIT" -> {
                // 菜品上架：由 DeepSeek 判断是否合理，合理直接通过上架，不合理驳回
                int updated = 0;
                String lastAction = null;
                String lastReply = null;
                Long adminId = UserContext.getUserId();
                for (Long id : ids) {
                    Dish dish = dishMapper.selectById(id);
                    if (dish == null || !Dish.AUDIT_PENDING.equals(dish.getAuditStatus())) {
                        continue;
                    }
                    DishDecision decision = judgeDish(dish);
                    try {
                        if (decision.approved) {
                            dishAuditService.approve(id, adminId);
                            lastAction = "审核通过并上架";
                        } else {
                            dishAuditService.reject(id, adminId, decision.reason);
                            lastAction = "审核驳回";
                        }
                        lastReply = decision.reason;
                        // 审核结果同步通知管理员（工作台铃铛可见）
                        notificationService.notifyAdmins(
                                com.qmz.canteen.entity.Notification.TYPE_ADMIN_NOTICE,
                                "AI 已审核菜品「" + dish.getName() + "」："
                                        + (decision.approved ? "通过并上架" : "驳回（" + decision.reason + "）"),
                                "/admin/dish-audits", id);
                        updated++;
                    } catch (Exception e) {
                        log.warn("AI 审核菜品 {} 失败：{}", id, e.getMessage());
                    }
                }
                return new HandleOutcome(updated > 0, lastAction, lastReply, updated);
            }
            default -> {
                return HandleOutcome.none();
            }
        }
    }

    /** 优先用聚类下全部原始 ID；空则退回代表 sourceId。 */
    private List<Long> ids(AdminAiFindingVO finding) {
        if (finding.getItemIds() != null && !finding.getItemIds().isEmpty()) {
            return finding.getItemIds();
        }
        return List.of(finding.getSourceId());
    }

    private record DishDecision(boolean approved, String reason) {
    }

    /**
     * 菜品上架审核。分两层：
     * <ol>
     * <li>硬规则闸门（AI 之前）：无图片、名称乱码、营销广告、价格离谱——直接驳回，
     *     AI 无权推翻；AI 挂掉时兜底也绝不可能把垃圾菜放进库。</li>
     * <li>AI 判断「信息整体像不像一道正经菜」，只对通过硬闸门的候选起作用。</li>
     * </ol>
     */
    private DishDecision judgeDish(Dish dish) {
        // ---- 第一层：硬规则（图片必填 / 乱码 / 营销 / 价格）----
        DishDecision hard = hardGate(dish);
        if (hard != null) {
            return hard;
        }

        // ---- 第二层：AI 判断（到这里图片一定有、名称一定是像样的文字）----
        boolean ruleOk = dish.getPrice() != null && dish.getPrice().signum() >= 0
                && dish.getPrice().doubleValue() <= 100;
        try {
            if (!agentProperties.isEnabled()) {
                return new DishDecision(ruleOk, ruleOk ? "信息完整，自动通过" : "价格异常，建议修改");
            }
            String user = "菜品名称：" + dish.getName()
                    + "；价格：" + dish.getPrice() + "元"
                    + "；分类：" + (dish.getCategory() == null || dish.getCategory().isBlank()
                    ? "未填" : dish.getCategory())
                    + "；描述：" + (dish.getDescription() == null || dish.getDescription().isBlank()
                    ? "无" : dish.getDescription())
                    + "；图片：已上传";
            AiResult result = router.complete(ModelRole.REPORT, prompts.getDishAudit(), user, 120, 0.2);
            String raw = result.content();
            if (raw != null && !raw.isBlank()) {
                String json = raw;
                int s = raw.indexOf('{');
                int e = raw.lastIndexOf('}');
                if (s >= 0 && e > s) {
                    json = raw.substring(s, e + 1);
                }
                JsonNode node = MAPPER.readTree(json);
                boolean approved = node.path("approved").asBoolean(ruleOk);
                String reason = node.path("reason").asText(approved ? "信息合理，自动通过" : "信息异常，建议修改");
                return new DishDecision(approved, reason);
            }
        } catch (Exception e) {
            log.warn("AI 判断菜品 {} 失败，退回规则判断：{}", dish.getId(), e.getMessage());
        }
        return new DishDecision(ruleOk, ruleOk ? "信息完整，自动通过" : "价格异常，建议修改");
    }

    /** 硬闸门：命中任何一条 → 直接驳回（返回 decision）；都没命中 → 返回 null 放行给 AI。 */
    private DishDecision hardGate(Dish dish) {
        // 1. 图片必填：学生端菜品卡靠图选菜，无图菜品一律驳回
        if (dish.getImage() == null || dish.getImage().isBlank()) {
            return new DishDecision(false, "未上传菜品图片，请补充图片后重新提交");
        }
        // 2. 名称必须是像样的菜名：剥掉表情/符号/标点/空白后，有效字符太少=乱码
        if (!isRealDishName(dish.getName())) {
            return new DishDecision(false, "菜品名称异常（乱码、符号或信息过少），请填写真实菜名");
        }
        // 3. 名称或描述含营销/引流关键词：一律驳回
        String hay = (dish.getName() == null ? "" : dish.getName())
                + " " + (dish.getDescription() == null ? "" : dish.getDescription());
        String spam = findSpamKeyword(hay);
        if (spam != null) {
            return new DishDecision(false, "含违规营销内容（" + spam + "），不允许上架");
        }
        // 4. 价格闸门：未填或低于 1 元（0 元霸王餐）或超过 100 元
        if (dish.getPrice() == null) {
            return new DishDecision(false, "未填写价格，请补充价格后重新提交");
        }
        if (dish.getPrice().doubleValue() < 1 || dish.getPrice().doubleValue() > 100) {
            return new DishDecision(false, "价格异常（" + dish.getPrice() + "元），请填写 1~100 元内的真实价格");
        }
        return null;
    }

    /**
     * 名称质量判断：去掉表情符号、标点、数字、空白后，剩余的
     * 中文/字母有效字符不足 2 个 → 视为乱码或无意义名称。
     */
    private boolean isRealDishName(String name) {
        if (name == null) {
            return false;
        }
        int valid = 0;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            // CJK 统一表意文字（含扩展A）或普通字母，才算有效字符
            boolean cjk = (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF);
            boolean letter = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
            if (cjk || letter) {
                valid++;
            }
        }
        return valid >= 2;
    }

    private static final String[] SPAM_KEYWORDS = {
            "免费", "加微信", "加vx", "加VX", "加Vx", "加v信", "微信", "vx号", "VX号",
            "集赞", "转发", "霸王餐", "领红包", "扫码", "二维码", "进群", "入群",
            "低价代购", "代购", "刷单", "兼职", "博彩", "赌博", "裸聊", "外挂"
    };

    /** 返回命中的第一个营销/引流关键词；没有则返回 null。 */
    private String findSpamKeyword(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String kw : SPAM_KEYWORDS) {
            if (lower.contains(kw.toLowerCase(Locale.ROOT))) {
                return kw;
            }
        }
        return null;
    }

    /**
     * 建议/表扬类留言（OBSERVE）：无需回复，仅标记已处理，让其退出工作台待办。
     */
    @Transactional
    public boolean markFeedbackResolved(AdminAiFindingVO finding) {
        if (finding == null || !"FEEDBACK".equals(finding.getSourceType())) {
            return false;
        }
        int updated = 0;
        for (Long id : ids(finding)) {
            Feedback feedback = feedbackMapper.selectById(id);
            if (feedback == null || !"PENDING".equals(feedback.getStatus())
                    && !"PROCESSING".equals(feedback.getStatus())) {
                continue;
            }
            feedback.setStatus(FeedbackStatus.RESOLVED.name());
            feedbackMapper.updateById(feedback);
            updated++;
        }
        return updated > 0;
    }
}
