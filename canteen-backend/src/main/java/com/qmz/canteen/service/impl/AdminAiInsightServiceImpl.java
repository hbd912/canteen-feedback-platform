package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qmz.canteen.agent.AgentProperties;
import com.qmz.canteen.agent.AiResult;
import com.qmz.canteen.agent.ModelRole;
import com.qmz.canteen.agent.MultiModelRouter;
import com.qmz.canteen.common.enums.FeedbackType;
import com.qmz.canteen.entity.Dish;
import com.qmz.canteen.entity.DishReview;
import com.qmz.canteen.entity.Feedback;
import com.qmz.canteen.entity.MerchantMessage;
import com.qmz.canteen.entity.RefundRequest;
import com.qmz.canteen.entity.Stall;
import com.qmz.canteen.mapper.DishMapper;
import com.qmz.canteen.mapper.DishReviewMapper;
import com.qmz.canteen.mapper.FeedbackMapper;
import com.qmz.canteen.mapper.MerchantMessageMapper;
import com.qmz.canteen.mapper.RefundRequestMapper;
import com.qmz.canteen.mapper.StallMapper;
import com.qmz.canteen.service.AdminAiInsightService;
import com.qmz.canteen.vo.AdminAiAnswerVO;
import com.qmz.canteen.vo.AdminAiFindingVO;
import com.qmz.canteen.vo.AdminAiInsightVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工作台 AI 值班助理。
 * <p>
 * 重要边界：这里只读业务表，只产出研判与回复草稿；不退款、不发券、不处罚、不上架。
 * 所有数字由 SQL/Java 统计，模型只负责摘要与话术，模型失败时退回规则结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAiInsightServiceImpl implements AdminAiInsightService {

    private static final int WINDOW_DAYS = 7;
    private static final int FETCH_LIMIT = 60;
    private static final int CACHE_MINUTES = 10;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FeedbackMapper feedbackMapper;
    private final DishReviewMapper dishReviewMapper;
    private final MerchantMessageMapper merchantMessageMapper;
    private final RefundRequestMapper refundRequestMapper;
    private final DishMapper dishMapper;
    private final StallMapper stallMapper;
    private final MultiModelRouter router;
    private final AgentProperties agentProperties;
    private final AdminAutoHandleService autoHandleService;
    private final com.qmz.canteen.agent.PromptProperties prompts;
    private final com.qmz.canteen.service.NotificationService notificationService;

    private final Object refreshLock = new Object();
    private volatile AdminAiInsightVO cached;
    private volatile LocalDateTime cachedAt;

    @Override
    public AdminAiInsightVO getCached() {
        AdminAiInsightVO current = cached;
        if (current == null) {
            AdminAiInsightVO empty = new AdminAiInsightVO();
            empty.setStatus("EMPTY");
            empty.setSummary("还没有整理，我这就看看当前的留言和退款。");
            empty.setWindowDays(WINDOW_DAYS);
            return empty;
        }
        current.setStale(cachedAt != null
                && cachedAt.isBefore(LocalDateTime.now().minusMinutes(CACHE_MINUTES)));
        return current;
    }

    @Override
    public AdminAiInsightVO refresh() {
        synchronized (refreshLock) {
            AdminAiInsightVO vo = build();
            cached = vo;
            cachedAt = LocalDateTime.now();
            return vo;
        }
    }

    @Override
    public AdminAiInsightVO handleAll() {
        synchronized (refreshLock) {
            int handled = 0;
            List<AdminAiFindingVO> handledItems = new ArrayList<>();
            List<AdminAiInsightVO.HandledDetail> details = new ArrayList<>();
            // 循环处理直到清空（每轮 build 只取最近 60 条/类，处理完再取下一批）
            for (int round = 0; round < 20; round++) {
                AdminAiInsightVO vo = build();
                int roundHandled = 0;
                for (AdminAiFindingVO finding : vo.getAutoHandleable()) {
                    var outcome = autoHandleService.autoHandle(finding);
                    if (outcome.handled()) {
                        roundHandled++;
                        handledItems.add(finding);
                        details.add(toDetail(finding, outcome));
                    }
                }
                for (AdminAiFindingVO finding : vo.getNeedAction()) {
                    var outcome = autoHandleService.autoHandleForce(finding);
                    if (outcome.handled()) {
                        roundHandled++;
                        handledItems.add(finding);
                        details.add(toDetail(finding, outcome));
                    }
                }
                // 建议/表扬类留言：无需回复，标记已处理让工作台待办归零
                for (AdminAiFindingVO finding : vo.getObserve()) {
                    if (autoHandleService.markFeedbackResolved(finding)) {
                        roundHandled++;
                        handledItems.add(finding);
                        details.add(toDetail(finding,
                                new AdminAutoHandleService.HandleOutcome(true, "已标记处理", null, 1)));
                    }
                }
                handled += roundHandled;
                if (roundHandled == 0) {
                    break;
                }
            }
            AdminAiInsightVO fresh = build();
            fresh.setAutoHandled(true);
            fresh.setAutoHandledCount(fresh.getAutoHandledCount() + handled);
            List<AdminAiFindingVO> merged = new ArrayList<>(fresh.getAutoHandledItems());
            merged.addAll(handledItems);
            fresh.setAutoHandledItems(merged);
            fresh.setHandledDetails(details);
            warnMerchantsForSerious(fresh);
            fresh.setSummary(summarizeHandled(handled, fresh, details));
            cached = fresh;
            cachedAt = LocalDateTime.now();
            return fresh;
        }
    }

    /** 把处理结果转成前端可展示的明细行 */
    private AdminAiInsightVO.HandledDetail toDetail(AdminAiFindingVO finding,
                                                    AdminAutoHandleService.HandleOutcome outcome) {
        AdminAiInsightVO.HandledDetail d = new AdminAiInsightVO.HandledDetail();
        d.setSourceType(finding.getSourceType());
        d.setSourceLabel(switch (finding.getSourceType() == null ? "" : finding.getSourceType()) {
            case "FEEDBACK" -> "学生留言";
            case "REVIEW" -> "低分评价";
            case "MERCHANT_MESSAGE" -> "商家留言";
            case "DISH_AUDIT" -> "菜品审核";
            default -> "其他";
        });
        d.setTitle(finding.getTitle());
        d.setAction(outcome.action());
        d.setReply(outcome.reply());
        d.setCount(Math.max(outcome.count(), 1));
        return d;
    }

    /** 严重卫生/食品安全问题：给对应档口商家发警告（同档口同类 6 小时内只警告一次） */
    private void warnMerchantsForSerious(AdminAiInsightVO vo) {
        for (AdminAiFindingVO finding : vo.getNeedAction()) {
            if (!"P0".equals(finding.getSeverity())) {
                continue;
            }
            boolean serious = "FOOD_SAFETY".equals(finding.getCategory())
                    || "HYGIENE".equals(finding.getCategory());
            if (!serious || finding.getStallId() == null) {
                continue;
            }
            String key = finding.getStallId() + ":" + finding.getCategory();
            LocalDateTime last = warnedAt.get(key);
            if (last != null && last.isAfter(LocalDateTime.now().minusHours(6))) {
                continue;
            }
            notificationService.notifyMerchant(finding.getStallId(),
                    com.qmz.canteen.entity.Notification.TYPE_ADMIN_NOTICE,
                    "【警告】学生反馈「" + finding.getTitle() + "」属" +
                            ("FOOD_SAFETY".equals(finding.getCategory()) ? "食品安全" : "卫生") +
                            "严重问题，平台已记录并会复核，请立即整改并回复学生。",
                    "/merchant/messages", null);
            warnedAt.put(key, LocalDateTime.now());
        }
    }

    private final Map<String, LocalDateTime> warnedAt = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public AdminAiInsightVO handleAutoOnly() {
        synchronized (refreshLock) {
            int handled = 0;
            List<AdminAiFindingVO> handledItems = new ArrayList<>();
            List<AdminAiInsightVO.HandledDetail> details = new ArrayList<>();
            for (int round = 0; round < 20; round++) {
                AdminAiInsightVO vo = build();
                int roundHandled = 0;
                // 只处理免审核的（可自动处理的非敏感项），敏感的仍留给你
                for (AdminAiFindingVO finding : vo.getAutoHandleable()) {
                    var outcome = autoHandleService.autoHandle(finding);
                    if (outcome.handled()) {
                        roundHandled++;
                        handledItems.add(finding);
                        details.add(toDetail(finding, outcome));
                    }
                }
                // 建议/表扬类留言标记已处理
                for (AdminAiFindingVO finding : vo.getObserve()) {
                    if (autoHandleService.markFeedbackResolved(finding)) {
                        roundHandled++;
                        handledItems.add(finding);
                        details.add(toDetail(finding,
                                new AdminAutoHandleService.HandleOutcome(true, "已标记处理", null, 1)));
                    }
                }
                handled += roundHandled;
                if (roundHandled == 0) {
                    break;
                }
            }
            AdminAiInsightVO fresh = build();
            fresh.setAutoHandled(true);
            fresh.setAutoHandledCount(fresh.getAutoHandledCount() + handled);
            List<AdminAiFindingVO> merged = new ArrayList<>(fresh.getAutoHandledItems());
            merged.addAll(handledItems);
            fresh.setAutoHandledItems(merged);
            fresh.setHandledDetails(details);
            warnMerchantsForSerious(fresh);
            fresh.setSummary(summarizeHandled(handled, fresh, details));
            cached = fresh;
            cachedAt = LocalDateTime.now();
            return fresh;
        }
    }

    @Override
    public String greeting() {
        long feedbacks = feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                .in(Feedback::getStatus, "PENDING", "PROCESSING"));
        long reviews = dishReviewMapper.selectCount(new LambdaQueryWrapper<DishReview>()
                .eq(DishReview::getStatus, DishReview.STATUS_PUBLISHED)
                .eq(DishReview::getHandled, 0)
                .le(DishReview::getRating, 3));
        long messages = merchantMessageMapper.selectCount(new LambdaQueryWrapper<MerchantMessage>()
                .eq(MerchantMessage::getStatus, 0)
                .eq(MerchantMessage::getDirection, MerchantMessage.DIR_IN));
        long refunds = refundRequestMapper.selectCount(new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getStatus, RefundRequest.STATUS_PENDING));
        // 退款不计入待办：审核由商家端负责，管理端只记录
        long total = feedbacks + reviews + messages;
        String fallback = total == 0
                ? "今天没有待处理的事项，一切正常，辛苦了。"
                : "今天有 " + total + " 项待处理（学生留言 " + feedbacks + "、低分评价 " + reviews
                + "、商家留言 " + messages + "），我帮你处理吧。";
        try {
            if (!agentProperties.isEnabled()) {
                return fallback;
            }
            String user = "当前待办统计：学生留言 " + feedbacks + " 条、低分评价 " + reviews
                    + " 条、商家留言 " + messages + " 条，共 " + total + " 项。"
                    + "（另有退款记录 " + refunds + " 笔，由商家端审核，不属于管理员待办，不要列进待办。）";
            AiResult result = router.complete(ModelRole.REPORT, prompts.getGreeting(), user, 150, 0.5);
            String text = result.content();
            if (StringUtils.hasText(text)) {
                return truncate(text.replaceAll("\\s+", " ").trim(), 150);
            }
        } catch (Exception e) {
            log.warn("生成开场白失败，返回规则文案：{}", e.getMessage());
        }
        return fallback;
    }

    @Override
    public AdminAiAnswerVO ask(String question) {
        long started = System.currentTimeMillis();
        AdminAiInsightVO insight = cached == null ? refresh() : cached;
        AdminAiAnswerVO vo = new AdminAiAnswerVO();
        String fallback = deterministicAnswer(question, insight);
        if (!agentProperties.isEnabled() || insight.getTotalItems() == 0) {
            vo.setAnswer(fallback);
            vo.setModel("rules");
            vo.setDegraded(true);
            vo.setCostMs(System.currentTimeMillis() - started);
            return vo;
        }
        try {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("summary", insight.getSummary());
            context.put("coverage", insight.getCoverage());
            context.put("needAction", insight.getNeedAction().stream().limit(6)
                    .map(this::briefFinding).toList());
            context.put("soothe", insight.getSoothe().stream().limit(5)
                    .map(this::briefFinding).toList());

            String system = "你是校园食堂管理员的值班助理。只根据提供的当前研判回答，不得编造数字、订单、学生姓名或事实。"
                    + "回答控制在120字内，使用中文纯文本，不要 Markdown。"
                    + "退款由商家端审核、平台只做记录，不要建议管理员去审核或处理退款；处罚、上架审核只能建议管理员人工确认，不得声称已经执行。";
            String user = "当前研判：" + MAPPER.writeValueAsString(context)
                    + "\n管理员问题：" + question;
            AiResult result = router.complete(ModelRole.BRAIN, system, user, 500, 0.3);
            String answer = result.content();
            if (!StringUtils.hasText(answer)) {
                throw new IllegalStateException("empty answer");
            }
            vo.setAnswer(truncate(answer.replaceAll("\\s+", " ").trim(), 300));
            vo.setModel(result.model());
            vo.setDegraded(false);
            vo.setCostMs(result.costMs());
        } catch (Exception e) {
            log.warn("工作台值班助理回答失败，返回规则结果：{}", e.getMessage());
            vo.setAnswer(fallback);
            vo.setModel("rules");
            vo.setDegraded(true);
            vo.setCostMs(System.currentTimeMillis() - started);
        }
        return vo;
    }

    private Map<String, Object> briefFinding(AdminAiFindingVO finding) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", finding.getId());
        row.put("title", finding.getTitle());
        row.put("severity", finding.getSeverity());
        row.put("count", finding.getCount());
        row.put("users", finding.getUsers());
        row.put("sourceType", finding.getSourceType());
        row.put("suggestedReply", finding.getSuggestedReply());
        return row;
    }

    private String deterministicAnswer(String question, AdminAiInsightVO insight) {
        if (insight == null || insight.getTotalItems() == 0) {
            // 待办为 0 时仍可回答退款咨询：退款记录单独存在于 coverage
            if (question != null && question.contains("退款")) {
                int records = insight == null ? 0 : coverageCount(insight, "REFUND");
                return "退款只做记录，审核由商家端处理；当前有 " + records + " 笔退款记录，不需要你在后台审核。";
            }
            return "当前没有需要整理的留言和评价。";
        }
        String q = question == null ? "" : question.toLowerCase();
        if (q.contains("退款")) {
            return "退款只做记录，审核由商家端处理；当前有 "
                    + coverageCount(insight, "REFUND") + " 笔退款记录。";
        }
        if (q.contains("食品安全") || q.contains("卫生")) {
            List<AdminAiFindingVO> safety = insight.getNeedAction().stream()
                    .filter(item -> "FOOD_SAFETY".equals(item.getCategory())
                            || "HYGIENE".equals(item.getCategory()))
                    .toList();
            if (!safety.isEmpty()) {
                return "需要优先核查：" + joinTitles(safety, 4) + "。请先现场核实并保留证据。";
            }
        }
        if (q.contains("安抚") || q.contains("回复")) {
            List<AdminAiFindingVO> soothe = insight.getSoothe();
            if (!soothe.isEmpty()) {
                return "可以先安抚：" + joinTitles(soothe, 4)
                        + "。建议回复已按条目放在下方草稿里，发送前请确认。";
            }
        }
        if (q.contains("先") || q.contains("优先") || q.contains("处理")) {
            List<AdminAiFindingVO> need = insight.getNeedAction();
            if (!need.isEmpty()) {
                return "先处理：" + joinTitles(need, 4) + "。食品安全、退款金额和差评集中问题优先。";
            }
        }
        return insight.getSummary();
    }

    private String joinTitles(List<AdminAiFindingVO> findings, int limit) {
        return findings.stream().limit(limit)
                .map(item -> item.getSeverityLabel() + " · " + item.getTitle())
                .collect(Collectors.joining("；"));
    }

    private AdminAiInsightVO build() {
        LocalDateTime now = LocalDateTime.now();

        long feedbackTotal = feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                .in(Feedback::getStatus, "PENDING", "PROCESSING"));
        List<Feedback> feedbacks = feedbackMapper.selectPage(new Page<>(1, FETCH_LIMIT),
                new LambdaQueryWrapper<Feedback>()
                        .in(Feedback::getStatus, "PENDING", "PROCESSING")
                        .orderByDesc(Feedback::getCreateTime)).getRecords();

        long reviewTotal = dishReviewMapper.selectCount(new LambdaQueryWrapper<DishReview>()
                .eq(DishReview::getStatus, DishReview.STATUS_PUBLISHED)
                .eq(DishReview::getHandled, 0)
                .le(DishReview::getRating, 3));
        List<DishReview> reviews = dishReviewMapper.selectPage(new Page<>(1, FETCH_LIMIT),
                new LambdaQueryWrapper<DishReview>()
                        .eq(DishReview::getStatus, DishReview.STATUS_PUBLISHED)
                        .eq(DishReview::getHandled, 0)
                        .le(DishReview::getRating, 3)
                        .orderByDesc(DishReview::getCreateTime)).getRecords();

        long messageTotal = merchantMessageMapper.selectCount(new LambdaQueryWrapper<MerchantMessage>()
                .eq(MerchantMessage::getStatus, 0)
                .eq(MerchantMessage::getDirection, MerchantMessage.DIR_IN));
        List<MerchantMessage> merchantMessages = merchantMessageMapper.selectPage(new Page<>(1, FETCH_LIMIT),
                new LambdaQueryWrapper<MerchantMessage>()
                        .eq(MerchantMessage::getStatus, 0)
                        .eq(MerchantMessage::getDirection, MerchantMessage.DIR_IN)
                        .orderByDesc(MerchantMessage::getCreateTime)).getRecords();

        // 退款只做记录、不由管理员审核（同意/驳回在商家端）：这里只取总数用于信息展示，
        // 绝不进入 items → 不参与研判、不计入待办总数、不出现"需要你手动"列表。
        long refundTotal = refundRequestMapper.selectCount(new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getStatus, RefundRequest.STATUS_PENDING));

        long dishAuditTotal = dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getAuditStatus, Dish.AUDIT_PENDING)
                .eq(Dish::getDeleted, 0));
        List<Dish> pendingDishes = dishMapper.selectPage(new Page<>(1, FETCH_LIMIT),
                new LambdaQueryWrapper<Dish>()
                        .eq(Dish::getAuditStatus, Dish.AUDIT_PENDING)
                        .eq(Dish::getDeleted, 0)
                        .orderByDesc(Dish::getCreateTime)).getRecords();

        Set<Long> stallIds = new LinkedHashSet<>();
        feedbacks.stream().map(Feedback::getStallId).filter(id -> id != null).forEach(stallIds::add);
        merchantMessages.stream().map(MerchantMessage::getStallId).filter(id -> id != null).forEach(stallIds::add);
        pendingDishes.stream().map(Dish::getStallId).filter(id -> id != null).forEach(stallIds::add);
        Set<Long> dishIds = reviews.stream().map(DishReview::getDishId)
                .filter(id -> id != null).collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, String> stallNames = stallIds.isEmpty()
                ? Map.of()
                : stallMapper.selectBatchIds(stallIds).stream()
                .collect(Collectors.toMap(Stall::getId, Stall::getName, (a, b) -> a));
        Map<Long, Dish> dishes = dishIds.isEmpty()
                ? Map.of()
                : dishMapper.selectBatchIds(dishIds).stream()
                .collect(Collectors.toMap(Dish::getId, dish -> dish, (a, b) -> a));
        dishes.values().stream().map(Dish::getStallId).filter(id -> id != null).forEach(stallIds::add);
        if (!stallIds.isEmpty()) {
            Map<Long, String> missing = stallMapper.selectBatchIds(stallIds).stream()
                    .collect(Collectors.toMap(Stall::getId, Stall::getName, (a, b) -> a));
            stallNames = new HashMap<>(stallNames);
            stallNames.putAll(missing);
        }

        List<SourceItem> items = new ArrayList<>();
        for (Feedback feedback : feedbacks) {
            items.add(new SourceItem("FEEDBACK", feedback.getId(), feedback.getUserId(),
                    feedback.getStallId(), stallNames.get(feedback.getStallId()), null, null,
                    feedback.getTitle(), feedback.getContent(), feedback.getType(),
                    null, null, feedback.getCreateTime()));
        }
        for (DishReview review : reviews) {
            Dish dish = review.getDishId() == null ? null : dishes.get(review.getDishId());
            items.add(new SourceItem("REVIEW", review.getId(), review.getUserId(),
                    dish == null ? null : dish.getStallId(),
                    dish == null ? null : stallNames.get(dish.getStallId()),
                    review.getDishId(), dish == null ? null : dish.getName(),
                    null, review.getContent(), null, review.getRating(), null, review.getCreateTime()));
        }
        for (MerchantMessage message : merchantMessages) {
            items.add(new SourceItem("MERCHANT_MESSAGE", message.getId(), message.getUserId(),
                    message.getStallId(), stallNames.get(message.getStallId()), null, null,
                    "商家留言", message.getContent(), null, null, null, message.getCreateTime()));
        }
        // 注意：退款不加入 items —— 审核归属商家端，管理员这里只留档查看（见 /admin/refunds）。
        for (Dish dish : pendingDishes) {
            items.add(new SourceItem("DISH_AUDIT", dish.getId(), null,
                    dish.getStallId(), stallNames.get(dish.getStallId()), dish.getId(), dish.getName(),
                    dish.getName(), dish.getDescription(), null, null, dish.getPrice(), dish.getCreateTime()));
        }

        AdminAiInsightVO vo = new AdminAiInsightVO();
        vo.setStatus("READY");
        vo.setGeneratedAt(now);
        vo.setWindowDays(WINDOW_DAYS);
        // 待办总数不含退款：退款归商家端审核，管理端只记录
        vo.setTotalItems((int) (feedbackTotal + reviewTotal + messageTotal + dishAuditTotal));
        vo.setAnalyzedItems(items.size());
        vo.setTruncated(feedbackTotal > feedbacks.size() || reviewTotal > reviews.size()
                || messageTotal > merchantMessages.size()
                || dishAuditTotal > pendingDishes.size());
        vo.getCoverage().add(new AdminAiInsightVO.Coverage("FEEDBACK", "学生留言", (int) feedbackTotal));
        vo.getCoverage().add(new AdminAiInsightVO.Coverage("REVIEW", "低分评价", (int) reviewTotal));
        vo.getCoverage().add(new AdminAiInsightVO.Coverage("MERCHANT_MESSAGE", "商家留言", (int) messageTotal));
        // 保留 REFUND 项用于问答/概览展示，但措辞是"记录"而非"待处理"
        vo.getCoverage().add(new AdminAiInsightVO.Coverage("REFUND", "退款记录（商家端审核）", (int) refundTotal));
        vo.getCoverage().add(new AdminAiInsightVO.Coverage("DISH_AUDIT", "菜品待审核", (int) dishAuditTotal));
        vo.setDegraded(false);
        vo.setModel("rules");

        List<AdminAiFindingVO> findings = analyze(items);
        for (AdminAiFindingVO finding : findings) {
            switch (finding.getSuggestedAction()) {
                case "HANDLE" -> vo.getNeedAction().add(finding);
                case "SOOTHE" -> vo.getSoothe().add(finding);
                default -> vo.getObserve().add(finding);
            }
        }
        vo.setNeedActionCount(vo.getNeedAction().size());
        vo.setSootheCount(vo.getSoothe().size());
        vo.setObserveCount(vo.getObserve().size());
        vo.setPrevalentCount(countPrevalent(findings));
        classify(vo);
        vo.setSummary(buildSummary(vo));
        if (!findings.isEmpty()) {
            enrichWithAi(vo, findings);
        }
        suggestAnnouncement(vo);
        return vo;
    }

    /**
     * 公告建议：不再只看卫生，而由「反馈数量 + 模型对严重度的研判」共同决定。
     * 规则初筛（数量集中 / P0 / 跨档口）→ 命中后让 DeepSeek 判断是否需要公告并拟标题正文，
     * 模型说不需要则不推荐；模型不可用时按规则给兜底公告。
     */
    private void suggestAnnouncement(AdminAiInsightVO vo) {
        // 规则初筛：同类问题 >=3 条、或跨档口、或存在 P0、或普遍度高
        List<AdminAiFindingVO> all = new ArrayList<>();
        all.addAll(vo.getNeedAction());
        all.addAll(vo.getAutoHandleable());
        all.addAll(vo.getObserve());
        AdminAiFindingVO trigger = all.stream()
                .filter(f -> f.getCount() >= 3
                        || "P0".equals(f.getSeverity())
                        || vo.getPrevalentCount() > 0)
                .max(Comparator.comparingInt(AdminAiFindingVO::getCount))
                .orElse(null);
        if (trigger == null) {
            vo.setAnnouncementSuggested(false);
            return;
        }
        // 模型研判：是否需要发布公告 + 拟公告
        String title = null;
        String content = null;
        try {
            if (agentProperties.isEnabled()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("totalItems", vo.getTotalItems());
                payload.put("needAction", vo.getNeedAction().stream().limit(6)
                        .map(f -> Map.of("title", f.getTitle(), "category", f.getCategoryLabel(),
                                "count", f.getCount(), "severity", f.getSeverity()))
                        .toList());
                payload.put("prevalent", vo.getPrevalentCount());
                AiResult result = router.complete(ModelRole.REPORT,
                        "你是校园食堂平台的值班助理。根据待办统计判断：这些问题是否需要向全体学生发布一条提醒公告。"
                                + "问题数量多、跨多个档口、或涉及食品安全等需要同学注意的情况 → 建议发布；"
                                + "零星个别口味/服务吐槽 → 不需要。"
                                + "只输出 JSON：{\"suggest\":布尔,\"title\":\"公告标题15字内\",\"content\":\"公告正文60-100字，"
                                + "口语化提醒同学注意并说明平台已介入，不点名羞辱个别档口\"}。不要输出其他内容。",
                        MAPPER.writeValueAsString(payload), 300, 0.3);
                JsonNode node = MAPPER.readTree(extractJson(result.content()));
                if (!node.path("suggest").asBoolean(false)) {
                    vo.setAnnouncementSuggested(false);
                    return;
                }
                title = node.path("title").asText("");
                content = node.path("content").asText("");
            }
        } catch (Exception e) {
            log.warn("公告建议模型研判失败，走规则兜底：{}", e.getMessage());
        }
        vo.setAnnouncementSuggested(true);
        vo.setAnnouncementTitle(StringUtils.hasText(title) ? title : "食堂用餐提醒公告");
        vo.setAnnouncementContent(StringUtils.hasText(content) ? content
                : "近期收到同学们关于" + trigger.getCategoryLabel() + "问题的集中反馈（共 " + trigger.getCount()
                + " 条），平台已介入核实并督促相关档口整改。请同学们就餐时留意，如遇问题及时反馈。");
    }

    /**
     * 分类：敏感项进 needAction（需人工），其余可自动处理的进 autoHandleable。
     * 统计阶段只分类、不写库；真正处理由 handleAll / handleAutoOnly 触发。
     */
    private void classify(AdminAiInsightVO vo) {
        List<AdminAiFindingVO> manual = new ArrayList<>();
        List<AdminAiFindingVO> auto = new ArrayList<>();
        for (AdminAiFindingVO finding : vo.getNeedAction()) {
            if (isSensitive(finding)) {
                manual.add(finding);
            } else {
                auto.add(finding);
            }
        }
        // 可安抚的 SOOTHE 项默认也可自动处理
        for (AdminAiFindingVO finding : vo.getSoothe()) {
            auto.add(finding);
        }
        vo.getNeedAction().clear();
        vo.getNeedAction().addAll(manual);
        vo.setNeedActionCount(manual.size());
        vo.getAutoHandleable().clear();
        vo.getAutoHandleable().addAll(auto);
        vo.setAutoHandleableCount(auto.size());
    }

    /** 敏感 = 需要人工确认，绝不自动处理 */
    private boolean isSensitive(AdminAiFindingVO finding) {
        if ("P0".equals(finding.getSeverity())) {
            return true;
        }
        return "FOOD_SAFETY".equals(finding.getCategory())
                || "HYGIENE".equals(finding.getCategory())
                || "REFUND".equals(finding.getCategory());
    }

    /**
     * 规则保底：分类、严重度、普遍度全部在本地算好，模型不能改数字。
     */
    List<AdminAiFindingVO> analyze(List<SourceItem> items) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (SourceItem item : items) {
            Category category = detectCategory(item);
            String key = category.name() + ":" + (item.stallId() == null ? 0 : item.stallId());
            Group group = groups.computeIfAbsent(key, ignored -> new Group(key, category, item.stallId()));
            group.accept(item, baseSeverity(item, category));
        }

        Map<String, Set<Long>> categoryStalls = new HashMap<>();
        Map<String, Integer> categoryTotals = new HashMap<>();
        for (Group group : groups.values()) {
            categoryTotals.merge(group.category.name(), group.count, Integer::sum);
            categoryStalls.computeIfAbsent(group.category.name(), ignored -> new HashSet<>())
                    .add(group.stallId == null ? 0L : group.stallId);
        }

        List<AdminAiFindingVO> findings = new ArrayList<>();
        for (Group group : groups.values()) {
            int stallCount = categoryStalls.getOrDefault(group.category.name(), Set.of()).size();
            findings.add(toFinding(group, stallCount));
        }
        findings.sort(Comparator
                .comparingInt((AdminAiFindingVO finding) -> severityRank(finding.getSeverity()))
                .thenComparingInt(finding -> sourcePriority(finding.getSourceType()))
                .thenComparing(AdminAiFindingVO::getCount, Comparator.reverseOrder())
                .thenComparing(AdminAiFindingVO::getTitle));
        return findings;
    }

    private AdminAiFindingVO toFinding(Group group, int stallCount) {
        String severity = group.severity;
        if ((group.count >= 3 || group.users.size() >= 3) && "P2".equals(severity)) {
            severity = "P1";
        }
        String action = actionFor(severity, group);
        AdminAiFindingVO vo = new AdminAiFindingVO();
        vo.setId(group.key);
        vo.setCategory(group.category.name());
        vo.setCategoryLabel(group.category.label);
        vo.setSeverity(severity);
        vo.setSeverityLabel(severityLabel(severity));
        vo.setCount(group.count);
        vo.setUsers(group.users.size());
        vo.setStallCount(stallCount);
        vo.setScopeLabel(stallCount > 1 ? "同类问题跨 " + stallCount + " 个档口" : "单档口");
        vo.setStallName(group.stallName);
        vo.setStallId(group.stallId);
        vo.setSourceType(group.sourceType);
        vo.setSourceId(group.sourceId);
        vo.setItemIds(group.itemIds);
        vo.setSample(truncate(group.sample, 140));
        vo.setTitle(buildTitle(group));
        vo.setReason(buildReason(severity, group));
        vo.setSuggestedAction(action);
        vo.setActionLabel(actionLabel(action));
        vo.setSuggestedReply(fallbackReply(group, action));
        vo.setTargetUrl(targetUrl(group.sourceType));
        vo.setEvidence(group.sources.entrySet().stream()
                .map(entry -> sourceLabel(entry.getKey()) + " " + entry.getValue() + " 条")
                .toList());
        vo.setConfidence(group.category == Category.OTHER ? 0.76 : 0.9);
        return vo;
    }

    private String buildTitle(Group group) {
        if ("REVIEW".equals(group.sourceType) && StringUtils.hasText(group.dishName)) {
            return group.dishName + " · " + group.category.label;
        }
        if ("MERCHANT_MESSAGE".equals(group.sourceType) && StringUtils.hasText(group.stallName)) {
            return group.stallName + " · 商家留言";
        }
        if ("REFUND".equals(group.sourceType) && StringUtils.hasText(group.stallName)) {
            return group.stallName + " · 退款申请";
        }
        if (StringUtils.hasText(group.stallName)) {
            return group.stallName + " · " + group.category.label;
        }
        return group.category.label + "反馈";
    }

    private String buildReason(String severity, Group group) {
        if ("P0".equals(severity)) {
            if (group.category == Category.FOOD_SAFETY || group.category == Category.HYGIENE) {
                return "涉及食品安全或卫生，需要立即核实并保留证据。";
            }
            if (group.category == Category.REFUND) {
                return "涉及退款金额，需要核对订单、优惠券和凭证后人工处理。";
            }
            return "风险较高，需要尽快人工处理。";
        }
        if (group.count >= 3 || group.users.size() >= 3) {
            return "近 7 天同一档口有 " + group.count + " 条同类反馈，涉及 "
                    + group.users.size() + " 位学生。";
        }
        if ("MERCHANT_MESSAGE".equals(group.sourceType)) {
            return "商家留言还没有回复。";
        }
        return "单条反馈，建议先回复安抚，暂不需要平台处罚。";
    }

    private String buildSummary(AdminAiInsightVO vo) {
        if (vo.getTotalItems() == 0) {
            return "当前没有需要整理的留言和评价。";
        }
        int feedback = coverageCount(vo, "FEEDBACK");
        int reviews = coverageCount(vo, "REVIEW");
        int messages = coverageCount(vo, "MERCHANT_MESSAGE");
        int refunds = coverageCount(vo, "REFUND");
        return "已统计：共 " + feedback + " 条学生留言、" + reviews + " 条低分评价、"
                + messages + " 条商家留言。"
                + vo.getNeedActionCount() + " 项需要你手动处理，"
                + vo.getAutoHandleableCount() + " 项我可以自动处理。"
                + (refunds > 0 ? "另有 " + refunds + " 笔退款记录（由商家端审核，不需你处理）。" : "");
    }

    private int countPrevalent(List<AdminAiFindingVO> findings) {
        Map<String, Integer> totals = new HashMap<>();
        Map<String, Set<String>> scopes = new HashMap<>();
        for (AdminAiFindingVO finding : findings) {
            totals.merge(finding.getCategory(), finding.getCount(), Integer::sum);
            scopes.computeIfAbsent(finding.getCategory(), ignored -> new HashSet<>())
                    .add(finding.getStallName() == null ? "unknown" : finding.getStallName());
        }
        return (int) totals.entrySet().stream()
                .filter(entry -> entry.getValue() >= 3 || scopes.get(entry.getKey()).size() > 1)
                .count();
    }

    private void enrichWithAi(AdminAiInsightVO vo, List<AdminAiFindingVO> findings) {
        long started = System.currentTimeMillis();
        try {
            if (!agentProperties.isEnabled()) {
                vo.setDegraded(true);
                vo.setCostMs(System.currentTimeMillis() - started);
                return;
            }
            List<Map<String, Object>> compact = findings.stream().limit(10).map(finding -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", finding.getId());
                row.put("title", finding.getTitle());
                row.put("category", finding.getCategoryLabel());
                row.put("severity", finding.getSeverity());
                row.put("count", finding.getCount());
                row.put("sourceType", finding.getSourceType());
                row.put("sample", truncate(finding.getSample(), 120));
                return row;
            }).toList();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("coverage", vo.getCoverage());
            payload.put("needAction", vo.getNeedActionCount());
            payload.put("needActionItems", vo.getNeedAction().stream().limit(8)
                    .map(f -> Map.of("category", f.getCategoryLabel(), "count", f.getCount(),
                            "stall", f.getStallName() == null ? "" : f.getStallName()))
                    .toList());
            payload.put("autoHandleableItems", vo.getAutoHandleable().stream().limit(8)
                    .map(f -> Map.of("category", f.getCategoryLabel(), "count", f.getCount(),
                            "stall", f.getStallName() == null ? "" : f.getStallName()))
                    .toList());
            payload.put("soothe", vo.getSootheCount());
            payload.put("items", compact);

            AiResult result = router.complete(ModelRole.REPORT, prompts.getInsight(),
                    MAPPER.writeValueAsString(payload), 900, 0.2);
            JsonNode root = MAPPER.readTree(extractJson(result.content()));
            String summary = root.path("summary").asText("");
            if (StringUtils.hasText(summary) && summary.length() <= 220) {
                vo.setSummary(summary.trim());
            }
            Map<String, AdminAiFindingVO> byId = findings.stream()
                    .collect(Collectors.toMap(AdminAiFindingVO::getId, finding -> finding, (a, b) -> a));
            Set<String> replyAllowed = findings.stream()
                    .filter(finding -> "SOOTHE".equals(finding.getSuggestedAction())
                            || ("HANDLE".equals(finding.getSuggestedAction())
                            && !"P0".equals(finding.getSeverity())
                            && ("FEEDBACK".equals(finding.getSourceType())
                            || "MERCHANT_MESSAGE".equals(finding.getSourceType()))))
                    .map(AdminAiFindingVO::getId)
                    .collect(Collectors.toSet());
            for (JsonNode reply : root.path("replies")) {
                String id = reply.path("id").asText("");
                String text = reply.path("reply").asText("");
                AdminAiFindingVO finding = byId.get(id);
                if (finding != null && replyAllowed.contains(id)
                        && StringUtils.hasText(text) && text.length() <= 200) {
                    finding.setSuggestedReply(text.trim());
                }
            }
            for (JsonNode note : root.path("notes")) {
                String id = note.path("id").asText("");
                String text = note.path("note").asText("");
                AdminAiFindingVO finding = byId.get(id);
                if (finding != null && StringUtils.hasText(text) && text.length() <= 120) {
                    finding.setAiNote(text.trim());
                }
            }
            vo.setModel(result.model());
            vo.setCostMs(result.costMs());
            vo.setDegraded(false);
        } catch (Exception e) {
            log.warn("工作台 AI 值班分析失败，返回规则结果：{}", e.getMessage());
            vo.setDegraded(true);
            vo.setModel("rules");
            vo.setCostMs(System.currentTimeMillis() - started);
        }
    }

    /** 用 DeepSeek 生成「处理完」的总结话术；失败则退回规则文案。 */
    private String summarizeHandled(int handled, AdminAiInsightVO fresh,
                                    List<AdminAiInsightVO.HandledDetail> details) {
        int remain = fresh.getNeedActionCount();
        List<String> remainTitles = fresh.getNeedAction().stream()
                .limit(5).map(AdminAiFindingVO::getTitle).toList();
        // 明细行：处理了什么、怎么回的
        List<Map<String, Object>> detailRows = new ArrayList<>();
        for (AdminAiInsightVO.HandledDetail d : details) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("类型", d.getSourceLabel());
            row.put("对象", d.getTitle());
            row.put("动作", d.getAction());
            if (StringUtils.hasText(d.getReply())) row.put("回复", truncate(d.getReply(), 60));
            row.put("条数", d.getCount());
            detailRows.add(row);
        }
        String fallback = handled > 0
                ? "本次共处理 " + handled + " 项，明细见下方。"
                + (remain == 0 ? " 目前没有需要你人工确认的了。"
                : " 剩下 " + remain + " 项需要你人工确认。")
                : "没有可代处理的内容，食品安全类仍需你人工确认（退款由商家端审核，不在此列）。";
        try {
            if (!agentProperties.isEnabled()) {
                return fallback;
            }
            String user = "本次处理明细（JSON）：" + MAPPER.writeValueAsString(detailRows)
                    + "。剩余需人工确认 " + remain + " 项："
                    + (remainTitles.isEmpty() ? "无" : String.join("、", remainTitles))
                    + "。请用 2-3 句简短的话向管理员汇报：处理了哪些、怎么处理的（回了什么内容），"
                    + "以及还剩什么需要管理员确认。不要罗列表格，自然口语化。";
            AiResult result = router.complete(ModelRole.REPORT, prompts.getHandleSummary(), user, 300, 0.3);
            String text = result.content();
            if (StringUtils.hasText(text)) {
                return truncate(text.replaceAll("\\s+", " ").trim(), 260);
            }
        } catch (Exception e) {
            log.warn("生成处理总结失败，返回规则文案：{}", e.getMessage());
        }
        return fallback;
    }

    private String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return start >= 0 && end > start ? raw.substring(start, end + 1) : "{}";
    }

    private String fallbackReply(Group group, String action) {
        if ("OBSERVE".equals(action) || "REFUND".equals(group.sourceType)
                || group.category == Category.REFUND || "REVIEW".equals(group.sourceType)) {
            return "";
        }
        if ("MERCHANT_MESSAGE".equals(group.sourceType)) {
            return "收到你的反馈，我们已经记录，会尽快核实处理并回复你。";
        }
        if (group.category == Category.FOOD_SAFETY || group.category == Category.HYGIENE) {
            return "同学你好，非常抱歉出现这样的问题。你反馈的情况我们已经记录，会马上核实并处理，感谢你及时告诉我们。";
        }
        return "同学你好，抱歉这次体验不好。你反馈的问题我们已经记录，会尽快核实改进，感谢你的反馈。";
    }

    private Category detectCategory(SourceItem item) {
        if ("DISH_AUDIT".equals(item.sourceType())) {
            return Category.DISH;
        }
        if ("REFUND".equals(item.sourceType())) {
            return Category.REFUND;
        }
        String text = ((item.title() == null ? "" : item.title()) + " "
                + (item.text() == null ? "" : item.text())).toLowerCase();
        if (containsAny(text, "异物", "头发", "虫子", "变质", "发霉", "过期", "拉肚子", "腹泻",
                "食物中毒", "过敏", "异味", "馊", "不新鲜", "吃坏", "食品安全",
                "砧板", "生熟", "交叉污染")) {
            return Category.FOOD_SAFETY;
        }
        if (containsAny(text, "卫生", "脏", "不干净", "苍蝇", "蟑螂", "老鼠")) {
            return Category.HYGIENE;
        }
        if (containsAny(text, "退款", "退钱", "赔偿", "多收", "扣款", "少退", "金额", "差价")) {
            return Category.REFUND;
        }
        if (containsAny(text, "态度", "服务", "太慢", "等了", "出餐", "排队", "不理", "冷漠")) {
            return Category.SERVICE;
        }
        if (containsAny(text, "分量", "太少", "吃不饱", "量少", "缩水")) {
            return Category.PORTION;
        }
        if (containsAny(text, "太贵", "价格", "涨价", "性价比", "贵")) {
            return Category.PRICE;
        }
        if (containsAny(text, "难吃", "咸", "淡", "油腻", "口味", "不好吃", "腥")) {
            return Category.TASTE;
        }
        return Category.OTHER;
    }

    private String actionFor(String severity, Group group) {
        if ("P0".equals(severity) || "MERCHANT_MESSAGE".equals(group.sourceType)) {
            return "HANDLE";
        }
        if ("P1".equals(severity)) {
            if (group.category == Category.FOOD_SAFETY || group.category == Category.HYGIENE
                    || group.category == Category.REFUND || group.category == Category.SERVICE) {
                return "HANDLE";
            }
            if (group.rating != null && group.rating <= 2) {
                return "HANDLE";
            }
            return "SOOTHE";
        }
        if ("PRAISE".equals(group.feedbackType) || "SUGGEST".equals(group.feedbackType)) {
            return "OBSERVE";
        }
        if ("REVIEW".equals(group.sourceType) && group.rating != null && group.rating >= 4) {
            return "OBSERVE";
        }
        return "SOOTHE";
    }

    private String baseSeverity(SourceItem item, Category category) {
        if ("REFUND".equals(item.sourceType()) || category == Category.FOOD_SAFETY
                || category == Category.HYGIENE || category == Category.REFUND) {
            return "P0";
        }
        if ("DISH_AUDIT".equals(item.sourceType())) {
            return "P1";
        }
        if (item.amount() != null && item.amount().compareTo(new BigDecimal("50")) >= 0) {
            return "P0";
        }
        if ("MERCHANT_MESSAGE".equals(item.sourceType())) {
            return "P1";
        }
        if (item.rating() != null && item.rating() <= 2) {
            return "P1";
        }
        if (FeedbackType.COMPLAINT.name().equals(item.feedbackType())) {
            return "P1";
        }
        if (category == Category.SERVICE || category == Category.PRICE
                || category == Category.PORTION || category == Category.TASTE) {
            return "P1";
        }
        return "P2";
    }

    private String severityLabel(String severity) {
        return switch (severity) {
            case "P0" -> "立即处理";
            case "P1" -> "今日处理";
            default -> "观察";
        };
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "P0" -> 0;
            case "P1" -> 1;
            default -> 2;
        };
    }

    private int sourcePriority(String sourceType) {
        return switch (sourceType) {
            case "REFUND" -> 0;
            case "FEEDBACK" -> 1;
            case "MERCHANT_MESSAGE" -> 2;
            default -> 3;
        };
    }

    private String actionLabel(String action) {
        return switch (action) {
            case "HANDLE" -> "去处理";
            case "SOOTHE" -> "看建议";
            default -> "观察";
        };
    }

    private String targetUrl(String sourceType) {
        return switch (sourceType) {
            case "REVIEW" -> "/admin/feedback-center?tab=review";
            case "MERCHANT_MESSAGE" -> "/admin/feedback-center?tab=merchant";
            case "REFUND" -> "/admin/refunds";
            case "DISH_AUDIT" -> "/admin/dish-audits";
            default -> "/admin/feedback-center?tab=student";
        };
    }

    private String sourceLabel(String source) {
        return switch (source) {
            case "FEEDBACK" -> "学生留言";
            case "REVIEW" -> "评价";
            case "MERCHANT_MESSAGE" -> "商家留言";
            case "REFUND" -> "退款";
            default -> source;
        };
    }

    private int coverageCount(AdminAiInsightVO vo, String source) {
        return vo.getCoverage().stream()
                .filter(coverage -> source.equals(coverage.getSource()))
                .mapToInt(AdminAiInsightVO.Coverage::getCount)
                .findFirst().orElse(0);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String value = text.trim().replaceAll("\\s+", " ");
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    record SourceItem(String sourceType, Long sourceId, Long userId, Long stallId,
                      String stallName, Long dishId, String dishName, String title,
                      String text, String feedbackType, Integer rating,
                      BigDecimal amount, LocalDateTime createTime) {
    }

    private enum Category {
        FOOD_SAFETY("食品安全"),
        HYGIENE("卫生"),
        REFUND("退款与金额"),
        SERVICE("服务与出餐"),
        TASTE("口味"),
        PORTION("分量"),
        PRICE("价格"),
        DISH("菜品审核"),
        OTHER("其他");

        private final String label;

        Category(String label) {
            this.label = label;
        }
    }

    private static final class Group {
        private final String key;
        private final Category category;
        private final Long stallId;
        private int count;
        private final Set<Long> users = new HashSet<>();
        private final Map<String, Integer> sources = new LinkedHashMap<>();
        private String severity = "P2";
        private String sourceType;
        private Long sourceId;
        private final List<Long> itemIds = new ArrayList<>();
        private String stallName;
        private String dishName;
        private String title;
        private String sample;
        private String feedbackType;
        private Integer rating;
        private LocalDateTime latest;

        private Group(String key, Category category, Long stallId) {
            this.key = key;
            this.category = category;
            this.stallId = stallId;
        }

        private void accept(SourceItem item, String itemSeverity) {
            count++;
            if (item.sourceId() != null) {
                itemIds.add(item.sourceId());
            }
            if (item.userId() != null) {
                users.add(item.userId());
            }
            sources.merge(item.sourceType(), 1, Integer::sum);
            boolean replace = sourceType == null
                    || severityRankFor(itemSeverity) < severityRankFor(severity)
                    || (severityRankFor(itemSeverity) == severityRankFor(severity)
                    && (latest == null || (item.createTime() != null && item.createTime().isAfter(latest))));
            if (replace) {
                severity = itemSeverity;
                sourceType = item.sourceType();
                sourceId = item.sourceId();
                stallName = item.stallName();
                dishName = item.dishName();
                title = item.title();
                sample = StringUtils.hasText(item.text()) ? item.text() : item.title();
                feedbackType = item.feedbackType();
                rating = item.rating();
                latest = item.createTime();
            }
        }

        private static int severityRankFor(String severity) {
            return switch (severity) {
                case "P0" -> 0;
                case "P1" -> 1;
                default -> 2;
            };
        }
    }
}
