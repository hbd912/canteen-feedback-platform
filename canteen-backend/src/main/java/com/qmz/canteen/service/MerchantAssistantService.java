package com.qmz.canteen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qmz.canteen.agent.AiResult;
import com.qmz.canteen.agent.ModelRole;
import com.qmz.canteen.agent.MultiModelRouter;
import com.qmz.canteen.entity.AiEventLog;
import com.qmz.canteen.dto.DishDraftRequest;
import com.qmz.canteen.entity.Dish;
import com.qmz.canteen.entity.DishReview;
import com.qmz.canteen.entity.Stall;
import com.qmz.canteen.mapper.AiEventLogMapper;
import com.qmz.canteen.mapper.DishMapper;
import com.qmz.canteen.mapper.DishReviewMapper;
import com.qmz.canteen.mapper.OrderMapper;
import com.qmz.canteen.mapper.StallMapper;
import com.qmz.canteen.service.ExperienceService;
import com.qmz.canteen.vo.DishDraftVO;
import com.qmz.canteen.vo.MerchantAssistantAnswer;
import com.qmz.canteen.vo.SalesAnalysisVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家 ↔ Agent 对话：基于本档口真实数据回答，不编造。
 * <p>
 * 为了省 token：一次调用完成「读数据 + 回答」，不做多轮工具调用；
 * 上下文压到千字以内，输出限 500 token。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantAssistantService {

    private final DishMapper dishMapper;
    private final DishReviewMapper dishReviewMapper;
    private final OrderMapper orderMapper;
    private final StallMapper stallMapper;
    private final MultiModelRouter router;
    private final AiEventLogMapper eventLogMapper;
    private final ExperienceService experienceService;
    private final LocalImageService localImageService;
    private final SalesService salesService;
    private final DishDraftService dishDraftService;

    public MerchantAssistantAnswer ask(Long stallId, Long merchantUserId, String question, String image, String skill) {
        long start = System.currentTimeMillis();
        // 只传图、没写文字时补一个默认问题，避免空指针/空问题
        if ((question == null || question.isBlank()) && image != null && !image.isBlank()) {
            question = "upload".equals(skill) ? "请根据这张图生成上架草稿" : "帮我看看这张图";
        }
        if (question == null) {
            question = "";
        }
        String stallName = stallName(stallId);
        String snapshot = buildSnapshot(stallId);
        List<ExperienceService.ExperienceHit> hits = retrieveSafe(stallId, question);
        String imageDataUrl = localImageService.toDataUrl(image);
        boolean visionUsed = false;
        String answer;
        AiResult ai = null;

        // 「上传菜品」技能：商家在对话里传图/描述 → 直接返回上架草稿卡片（确认后才建，不走经营问答）
        MerchantAssistantAnswer draftAnswer = uploadSkill(stallId, question, image, skill);
        if (draftAnswer != null) {
            return draftAnswer;
        }

        // 需求：灵活的 agent。老板问销售/表格/分析时，我们用真实数据「生成表格」，
        // 而不是只给一句话。数据一律来自后端统计，AI 只负责解读，不编造数字。
        SalesAnalysisVO dataTool = mayGenerateData(stallId, question);

        try {
            String system = "你是校园食堂档口「" + stallName + "」的经营助理，帮老板看数据、想办法。"
                    + "只能基于下面提供的真实数据回答，不要编造任何数字。"
                    + "像一位靠谱的合伙人和老板对话：先给结论，再说依据，必要时分点展开，"
                    + "该详细就详细（150~400 字），不要挤成干巴巴的一句话。"
                    + "给建议要具体可执行：说清改什么、为什么、预期效果，不要只说「建议优化」。"
                    + "口语化、务实，避免空洞的客套。"
                    + "涉及改价、退款、发券、下架，只能给出建议，不能声称已经执行，并提醒老板确认。";
            String user = snapshot + experienceBlock(hits)
                    + (dataTool == null ? "" : "\n\n下面是一份真实统计，老板问到了销售分析，请围绕它展开：\n"
                    + dataTool.getSummary())
                    + "\n\n老板的问题：" + question;
            if (imageDataUrl != null) {
                // 有图：先让视觉模型识别图片，再结合问题回答；失败则降级为纯文字
                try {
                    ai = router.completeVision(system
                                    + " 商家上传了一张菜品图片，请先描述图片里的菜品（名称、主要食材、口味），再结合问题回答。",
                            user, imageDataUrl, 1024);
                    visionUsed = true;
                    answer = ai.content();
                } catch (Exception ve) {
                    log.warn("经营助理视觉识别失败，降级为纯文字：{}", ve.getMessage());
                    ai = router.complete(ModelRole.BRAIN, system, user, 1024);
                    answer = ai.content();
                }
            } else {
                ai = router.complete(ModelRole.BRAIN, system, user, 1024);
                answer = ai.content();
            }
        } catch (Exception e) {
            log.warn("商家经营助理模型调用失败，返回数据快照兜底：{}", e.getMessage());
            answer = "AI 服务暂时不可用，先给你一份数据快照：\n" + snapshot
                    + "\n建议先处理待处理差评和零销量菜品，具体执行仍需你确认。";
        }
        long cost = System.currentTimeMillis() - start;

        MerchantAssistantAnswer vo = new MerchantAssistantAnswer();
        vo.setAnswer(answer);
        vo.setDataSnapshot(snapshot);
        vo.setModel(ai == null ? "FALLBACK" : ai.model());
        vo.setTokens(ai == null ? 0 : ai.totalTokens());
        vo.setCostMs(cost);
        vo.setExperienceUsed(hits.size());
        vo.setVisionUsed(visionUsed);
        vo.setDetectedSkill(dataTool != null ? "data" : "chat");
        if (dataTool != null) {
            vo.setCharts(dataTool.getCharts());
            vo.setTables(List.of(dataTool.getTable(), dataTool.getDishTable()));
            vo.setDataDays(dataTool.getDays());
        }

        AiEventLog event = new AiEventLog();
        event.setEventType("MERCHANT_ASSISTANT");
        event.setSource("MERCHANT");
        event.setUserId(merchantUserId);
        event.setStallId(stallId);
        event.setContent(truncate(question, 1900));
        event.setDecisionJson("{\"assignTo\":\"MERCHANT\"}");
        event.setAssignTo("MERCHANT");
        event.setNeedHuman(0);
        event.setStatus("DONE");
        event.setResult(truncate(answer, 1500));
        event.setModelName(vo.getModel());
        event.setTokens(vo.getTokens());
        event.setCostMs((int) cost);
        eventLogMapper.insert(event);
        return vo;
    }

    /** 用真实订单 + 评价拼一个紧凑快照，既是模型输入，也是给商家核对 AI 有没有瞎说的依据 */
    private String buildSnapshot(Long stallId) {
        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getStallId, stallId)
                .eq(Dish::getStatus, 1));
        Map<Long, long[]> salesMap = new HashMap<>();
        long totalQty = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (Map<String, Object> row : orderMapper.aggregateStallDishSales(stallId, LocalDateTime.now().minusDays(30))) {
            Long dishId = toLong(row.get("dishId"));
            long qty = toLong(row.get("quantity"));
            salesMap.put(dishId, new long[]{qty});
            totalQty += qty;
            Object revenue = row.get("revenue");
            if (revenue instanceof Number n) {
                totalRevenue = totalRevenue.add(BigDecimal.valueOf(n.doubleValue()));
            }
        }

        Map<Long, long[]> reviewMap = new HashMap<>();
        Map<Long, Double> avgMap = new HashMap<>();
        long reviewTotal = 0;
        long badTotal = 0;
        for (Map<String, Object> row : dishReviewMapper.aggregateStallReviewStats(stallId)) {
            Long dishId = toLong(row.get("dishId"));
            long reviewCount = toLong(row.get("reviewCount"));
            long badCount = toLong(row.get("badCount"));
            reviewMap.put(dishId, new long[]{reviewCount, badCount});
            Object avg = row.get("avgRating");
            avgMap.put(dishId, avg == null ? 0.0 : ((Number) avg).doubleValue());
            reviewTotal += reviewCount;
            badTotal += badCount;
        }

        long pending = 0;
        List<Long> dishIds = dishes.stream().map(Dish::getId).toList();
        if (!dishIds.isEmpty()) {
            pending = dishReviewMapper.selectCount(new LambdaQueryWrapper<DishReview>()
                    .in(DishReview::getDishId, dishIds)
                    .eq(DishReview::getStatus, DishReview.STATUS_PUBLISHED)
                    .eq(DishReview::getHandled, 0));
        }

        List<String> weak = new ArrayList<>();
        for (Dish dish : dishes) {
            long qty = salesMap.containsKey(dish.getId()) ? salesMap.get(dish.getId())[0] : 0;
            long reviewCount = reviewMap.containsKey(dish.getId()) ? reviewMap.get(dish.getId())[0] : 0;
            long badCount = reviewMap.containsKey(dish.getId()) ? reviewMap.get(dish.getId())[1] : 0;
            double avg = avgMap.getOrDefault(dish.getId(), 0.0);
            if (qty == 0) {
                weak.add(dish.getName() + "（近30天零销量）");
            } else if (reviewCount >= 1 && badCount * 2 >= reviewCount) {
                weak.add(dish.getName() + "（差评占比高）");
            } else if (reviewCount >= 3 && avg <= 2.5) {
                weak.add(dish.getName() + "（平均评分 " + avg + "）");
            }
            if (weak.size() >= 3) {
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("档口：").append(stallName(stallId)).append("\n");
        sb.append("在售菜品：").append(dishes.size()).append(" 道\n");
        sb.append("近30天真实销量：").append(totalQty).append(" 份，营收 ¥")
                .append(totalRevenue.setScale(2, RoundingMode.HALF_UP)).append("\n");
        sb.append("评价：共 ").append(reviewTotal).append(" 条，其中差评 ").append(badTotal).append(" 条\n");
        sb.append("待处理评价：").append(pending).append(" 条\n");
        sb.append("值得关注的菜品：").append(weak.isEmpty() ? "暂无明显问题" : String.join("；", weak));
        return sb.toString();
    }

    /**
     * 「上传菜品」技能：老板在对话里传了图、或明确切到上传菜品技能时，走菜品草稿生成。
     * 草稿不落库，老板确认后才真正上架；返回的 draft 卡片在前端内联渲染。
     */
    private MerchantAssistantAnswer uploadSkill(Long stallId, String question, String image, String skill) {
        boolean inUploadSkill = "upload".equalsIgnoreCase(skill);
        boolean hasImage = localImageService.toDataUrl(image) != null;
        // 老板改主意了 / 否决当前任务：秒回确认，绝不生成草稿，也不让通用问答岔到经营数据上
        if (isCancelIntent(question) && (inUploadSkill || hasImage
                || containsAny(question, "上传", "上架", "草稿", "菜品"))) {
            MerchantAssistantAnswer vo = new MerchantAssistantAnswer();
            vo.setAnswer("好的，先不传了，刚才那步就停在这里，什么都不会提交。想重新上架时，把菜图或菜名发我就行。");
            vo.setDetectedSkill("chat");
            vo.setModel("rules");
            vo.setTokens(0);
            vo.setCostMs(0L);
            return vo;
        }
        // 仅在真的想把一道菜上架时才走菜单草稿；「写新菜推荐语」这类文案/问答不要误判成上架
        boolean isUploadIntent = inUploadSkill || hasImage
                || containsAny(question, "上架", "传菜品", "上传菜品", "生成菜品", "生成草稿", "菜品草稿");
        // 明显是经营问答不要再误判成上架（老板问某菜卖得怎么样）
        if (!inUploadSkill && !hasImage && containsAny(question, "卖得怎么样", "哪个该下架", "销量",
                "评价", "这个月赚", "营收", "分析", "表格", "客户")) {
            return null;
        }
        // 没图也没说清要上什么菜（口述只有"帮我上架"半截话、闲聊提问、或纯指令词）：
        // 不拿这句话本身当菜名去编草稿，转普通问答让老板把信息补全
        if (!hasImage && (!hasDishInfo(question) || looksLikeChitchat(question))) {
            return null;
        }
        if (!isUploadIntent) {
            return null;
        }
        MerchantAssistantAnswer vo = new MerchantAssistantAnswer();
        long start = System.currentTimeMillis();
        try {
            DishDraftRequest draftReq = new DishDraftRequest();
            draftReq.setImage(image);
            draftReq.setHint(question == null || question.isBlank() ? "" : question.trim());
            DishDraftVO draft = dishDraftService.draft(draftReq);
            vo.setDraft(draft);
            vo.setDetectedSkill("upload");
            vo.setModel(draft.getModel());
            vo.setTokens(draft.getTokens());
            vo.setCostMs(System.currentTimeMillis() - start);
            vo.setAnswer(draft.getNote() == null ? "" : draft.getNote());
            return vo;
        } catch (Exception e) {
            // 草稿失败（如视觉繁忙）：仍走普通经营问答兜底，不让对话死掉
            log.warn("上传菜品草稿生成失败，降级为经营问答：{}", e.getMessage());
            return null;
        }
    }

    /**
     * Agent 的「数据工具」：当老板在对话里提到销售/统计/表格/分析/卖得怎么样等意图时，
     * 就用真实数据生成一份表格 + 图表挂在回答里（默认近 7 天）。
     * 检测尽量宽松，让对话保持自然，不要求老板点固定按钮。
     */
    private SalesAnalysisVO mayGenerateData(Long stallId, String question) {
        String q = question == null ? "" : question.toLowerCase();
        boolean hasMonth = containsAny(q, "这月", "这个月", "本月", "这月度", "这三十天");
        boolean hasWeek = containsAny(q, "这周", "这一周", "本周");
        boolean wantsData = hasMonth || hasWeek || containsAny(q, "销售", "销额", "卖得", "卖怎么样",
                "统计", "分析", "表格", "报表", "数据", "营收", "收入", "订单表", "哪天", "看每天", "每天的",
                "热销", "生意");
        if (!wantsData) {
            return null;
        }
        // 区间：优先识别「近N天/最近N天/N天/N月」；识别不到时自然默认：提到月→近30，提到周→近7，否则近7
        int days = detectDays(q);
        if (days == 0) {
            days = hasMonth ? 30 : 7;
        }
        try {
            return salesService.aiAnalysis(stallId, days);
        } catch (Exception e) {
            log.warn("Agent 数据工具生成失败，忽略：{}", e.getMessage());
            return null;
        }
    }

    /** 从店长自由表述里解析区间：近7天/最近30天/14天/近1个月 → 天数 */
    private static int detectDays(String q) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:近|最近|过去|这)?\\s*(\\d{1,3})\\s*(天|日|月)").matcher(q);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            boolean isMonth = "月".equals(m.group(2));
            if (isMonth) {
                n *= 30;
            }
            return Math.min(Math.max(n, 1), 90);
        }
        // 提到这月/本月 → 近30天；这周 → 近7天；今天/昨天 → 近7天
        if (containsAny(q, "这月", "这个月", "本月", "这月度", "这三十天")) {
            return 30;
        }
        if (containsAny(q, "这周", "这一周", "本周")) {
            return 7;
        }
        return 0;
    }

    private static boolean containsAny(String q, String... keys) {
        for (String k : keys) {
            if (q.contains(k)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取消/放弃/否决当前任务：包括刚生成草稿后说"不要了"，也包括上传技能里改口"先不传了"。
     * 这类话绝不能被当成菜品描述去生成草稿。
     */
    private static boolean isCancelIntent(String q) {
        if (q == null || q.isBlank()) {
            return false;
        }
        String s = q.toLowerCase();
        // 例外：取消类动词 + 明确的重做指令（"取消刚才的，改上传鱼香肉丝"）不算取消
        if (containsAny(s, "改上", "换成", "改为", "重新上", "重传", "重新传")) {
            return false;
        }
        return containsAny(s, "取消", "不要上传", "不用上传", "不用传", "先不传", "不传了", "别传",
                "放弃", "算了吧", "不要了", "不需要了", "撤销", "作废", "删除草稿", "不要这个",
                "别弄了", "停下", "停止", "不搞了", "不弄了", "先不弄");
    }

    /** 口述内容是否包含可上架的菜品信息（菜名/食材/口味），只有"帮我上架"半截话不算 */
    private static boolean hasDishInfo(String q) {
        if (q == null || q.isBlank()) {
            return false;
        }
        // 纯指令词（只有动词没有对象）→ 没有菜品信息
        String stripped = q
                .replace("帮我上架", "").replace("帮我上传", "").replace("我要上架", "")
                .replace("上传菜品", "").replace("传菜品", "").replace("帮我传", "")
                .replace("生成草稿", "").replace("生成菜品", "").replace("上架一个", "")
                .replace("上架", "").replace("上传", "").replace("帮忙", "").replace("助手", "")
                .replace("你好", "").replace("请问", "").replace("麻烦", "").replace("谢谢", "");
        return !stripped.isBlank();
    }

    /** 自我认知/闲聊式提问（你叫什么、你是谁、天气……）：不是菜品信息，upload 技能下也不生成草稿 */
    private static boolean looksLikeChitchat(String q) {
        if (q == null || q.isBlank()) {
            return false;
        }
        return containsAny(q, "你是谁", "你叫什么", "什么名字", "名字是什么", "你会什么", "能干什么",
                "干什么用", "怎么用", "你是干嘛", "你是机器人", "几点", "天气", "你好吗", "在吗",
                "讲个笑话", "唱个歌", "多少钱话费");
    }

    private String stallName(Long stallId) {
        Stall stall = stallMapper.selectById(stallId);
        return stall == null ? "本档口" : stall.getName();
    }

    /** 商家点「这条有用」时，把问答沉淀成经验，下次同类问题可直接检索到 */
    public void feedback(Long stallId, String question, String answer, boolean helpful) {
        if (!helpful || question == null || answer == null) {
            return;
        }
        experienceService.save("MERCHANT_ASSISTANT", question, answer, "SUCCESS", 5,
                "商家认为有用", stallId, "FEEDBACK", null, true);
    }

    private List<ExperienceService.ExperienceHit> retrieveSafe(Long stallId, String question) {
        try {
            return experienceService.retrieve("MERCHANT_ASSISTANT", question, stallId, 3);
        } catch (Exception e) {
            log.warn("经营经验检索失败，忽略经验继续回答：{}", e.getMessage());
            return List.of();
        }
    }

    private String experienceBlock(List<ExperienceService.ExperienceHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n历史有效经营经验（只参考思路，不要照抄数字）：\n");
        for (ExperienceService.ExperienceHit hit : hits) {
            sb.append("- 问题：").append(shorten(hit.experience().getProblem(), 60))
                    .append(" → 做法：").append(shorten(hit.experience().getSolution(), 90)).append("\n");
        }
        return sb.toString();
    }

    private static String shorten(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
