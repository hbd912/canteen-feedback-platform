package com.qmz.canteen.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.qmz.canteen.agent.AiResult;
import com.qmz.canteen.agent.ModelRole;
import com.qmz.canteen.agent.MultiModelRouter;
import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.dto.DishDraftRequest;
import com.qmz.canteen.vo.DishDraftVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 菜品图片 → 上架草稿。
 * <p>
 * 原则：AI 只出草稿，商家确认后才真正创建；价格由商家定，AI 不碰。
 * 视觉模型不可用时会明确降级为「按文字生成」，绝不假装看过图片。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DishDraftService {

    private final MultiModelRouter router;
    private final LocalImageService localImageService;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private static final String JSON_RULE = "只输出 JSON 对象：{\"name\":\"菜名\",\"category\":\"分类\",\"description\":\"描述\",\"price\":建议售价数字}。"
            + "name 不超过 20 字；category 从 荤菜/素菜/汤品/主食/小吃/饮品 中选最合适的；"
            + "description 30-60 字，写主要食材、口味、口感卖点；"
            + "price 是你根据菜品用料/分量给出的建议售价（元，校园食堂常见价位 5~30，保留 1 位小数），不要过敏原、功效；"
            + "不要输出 JSON 以外的任何文字。";

    private static final String VISION_SYSTEM = "你是校园食堂的菜品上架助手。请根据图片和商家提供的信息，生成菜品上架草稿。" + JSON_RULE;

    private static final String TEXT_SYSTEM = "你是校园食堂的菜品上架助手。商家暂时没有提供可识别的图片，请只根据文字信息生成菜品上架草稿。"
            + "不要假装看过图片，也不要编造图片里没有的内容。" + JSON_RULE;

    private static final String REVISE_RULE = " 商家对上一版草稿不满意，请在上一版的基础上按反馈调整（菜名/描述可以微调，不要完全推翻重来）。";

    /** 只传图、没写说明时的默认生成提示：按本项目食堂档口的字段与定价习惯补全 */
    private static final String DEFAULT_VISION_HINT = "请根据这张菜品图片，为校园食堂档口生成上架信息："
            + "判断菜品名称；从 荤菜/素菜/汤品/主食/小吃/饮品 中选择分类；"
            + "写一段 30-60 字、能吸引学生下单的描述（主要食材、口味、口感）；"
            + "给出 5~30 元的合理建议售价。";

    public DishDraftVO draft(DishDraftRequest request) {
        String hint = buildHints(request);
        String imageDataUrl = localImageService.toDataUrl(request.getImage());
        boolean revise = hasText(request.getFeedback());
        String visionSystem = VISION_SYSTEM + (revise ? REVISE_RULE : "");
        String textSystem = TEXT_SYSTEM + (revise ? REVISE_RULE : "");
        // 打回重做时上一版草稿本身就是有效信息，不再要求额外提示
        boolean hasDishInfo = hasText(request.getName()) || hasText(request.getCategory())
                || hasText(request.getHint()) || revise;

        if (imageDataUrl != null) {
            // 没写说明时自动补上「生成菜品」的默认提示；写了说明则合并
            String visionUser = hasText(hint)
                    ? "商家补充：" + hint + " " + DEFAULT_VISION_HINT
                    : DEFAULT_VISION_HINT;
            // 网络瞬断时四家视觉可能同时失败：退避 1.2 秒再自动重试一轮
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    AiResult ai = router.completeVision(visionSystem, visionUser, imageDataUrl, 500);
                    Draft draft = parse(ai.content());
                    return buildVO(draft, request, true, ai,
                            revise ? "已按你的反馈调整，看看这版满意吗" : "已根据图片生成草稿和建议售价，请核对");
                } catch (Exception e) {
                    log.warn("菜品视觉草稿失败（第 {} 次），{}", attempt, e.getMessage());
                    if (attempt < 2) {
                        try {
                            Thread.sleep(1200);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
            log.warn("菜品视觉两轮均失败，尝试文字草稿或给出明确提示");
        }

        if (!hasDishInfo) {
            // 图片已经传了但视觉全部失败：明确告诉商家是模型繁忙，而不是误报「请先上传图片」
            if (imageDataUrl != null) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                        "视觉模型暂时繁忙（图片已保留），请再点一次「让 AI 上架」重试，或补一句菜品描述");
            }
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请先上传图片，或填写菜名/口味描述后再生成");
        }

        try {
            AiResult ai = router.complete(ModelRole.FAST, textSystem, hint, 350);
            Draft draft = parse(ai.content());
            String note = revise
                    ? "已按你的反馈调整，看看这版满意吗"
                    : (imageDataUrl == null
                            ? "未找到可用的图片，已根据文字信息生成草稿"
                            : "视觉模型暂不可用，已根据文字信息生成草稿，建议补充图片");
            return buildVO(draft, request, false, ai, note);
        } catch (Exception e) {
            log.warn("菜品文字草稿失败，使用确定性兜底：{}", e.getMessage());
            Draft draft = new Draft();
            draft.name = blankTo(request.getName(), "新菜品");
            draft.category = blankTo(request.getCategory(), "荤菜");
            draft.description = blankTo(request.getHint(),
                    draft.name + "，欢迎品尝。");
            DishDraftVO vo = buildVO(draft, request, false, null,
                    "AI 暂时不可用，已用你填写的信息生成草稿，请手动完善价格和描述");
            return vo;
        }
    }

    private String buildHints(DishDraftRequest request) {
        StringBuilder sb = new StringBuilder();
        if (hasText(request.getName())) {
            sb.append("菜名=").append(request.getName().trim()).append("；");
        }
        if (request.getPrice() != null) {
            sb.append("价格=").append(request.getPrice().stripTrailingZeros().toPlainString()).append(" 元；");
        }
        if (hasText(request.getCategory())) {
            sb.append("分类=").append(request.getCategory().trim()).append("；");
        }
        if (hasText(request.getHint())) {
            sb.append("商家补充=").append(request.getHint().trim()).append("；");
        }
        // 打回重做：带上上一版草稿和商家反馈，让 AI 在原基础上调整
        if (hasText(request.getPreviousName())) {
            sb.append("上一版菜名=").append(request.getPreviousName().trim()).append("；");
        }
        if (hasText(request.getPreviousCategory())) {
            sb.append("上一版分类=").append(request.getPreviousCategory().trim()).append("；");
        }
        if (hasText(request.getPreviousDescription())) {
            sb.append("上一版描述=").append(request.getPreviousDescription().trim()).append("；");
        }
        if (hasText(request.getFeedback())) {
            sb.append("商家不满意的地方=").append(request.getFeedback().trim()).append("。");
        }
        return sb.toString();
    }

    private Draft parse(String raw) {
        String json = extractJson(raw);
        if (json == null) {
            throw new IllegalStateException("模型未返回 JSON");
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            Draft draft = new Draft();
            draft.name = firstText(node, "name", "菜名", "dishName");
            draft.category = firstText(node, "category", "分类", "品类");
            draft.description = firstText(node, "description", "描述", "文案");
            draft.price = parsePrice(firstText(node, "price", "建议售价", "价格"));
            if (!hasText(draft.name) && !hasText(draft.description)) {
                throw new IllegalStateException("草稿缺少菜名和描述");
            }
            return draft;
        } catch (Exception e) {
            throw new IllegalStateException("草稿 JSON 解析失败：" + e.getMessage(), e);
        }
    }

    /** 解析 AI 建议价：容忍「¥12」「12元」等写法，夹到 1~99 元；解析失败返回 null */
    private static BigDecimal parsePrice(String raw) {
        if (!hasText(raw)) {
            return null;
        }
        String cleaned = raw.replaceAll("[^0-9.]", "");
        if (cleaned.isBlank() || ".".equals(cleaned)) {
            return null;
        }
        try {
            BigDecimal price = new BigDecimal(cleaned).setScale(1, java.math.RoundingMode.HALF_UP);
            if (price.compareTo(BigDecimal.ONE) < 0 || price.compareTo(BigDecimal.valueOf(99)) > 0) {
                return null;
            }
            return price;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private DishDraftVO buildVO(Draft draft, DishDraftRequest request, boolean visionUsed,
                                AiResult ai, String note) {
        DishDraftVO vo = new DishDraftVO();
        vo.setName(limit(blankTo(blankTo(draft.name, request.getName()), request.getPreviousName()), 60));
        vo.setCategory(limit(blankTo(blankTo(draft.category, request.getCategory()), request.getPreviousCategory()), 30));
        vo.setDescription(limit(blankTo(blankTo(draft.description, request.getHint()), request.getPreviousDescription()), 500));
        // 价格优先级：商家明确填价 > 上一版价格（除非反馈明确在说价格）> AI 建议价
        boolean priceFeedback = hasText(request.getFeedback())
                && (request.getFeedback().contains("价格") || request.getFeedback().contains("贵")
                || request.getFeedback().contains("便宜") || request.getFeedback().contains("定价")
                || request.getFeedback().contains("元") || request.getFeedback().contains("钱"));
        BigDecimal price;
        boolean suggested = false;
        if (request.getPrice() != null) {
            price = request.getPrice();
        } else if (request.getPreviousPrice() != null && !priceFeedback) {
            price = request.getPreviousPrice();
        } else {
            price = draft.price;
            suggested = draft.price != null;
        }
        vo.setPrice(price);
        vo.setPriceSuggested(suggested);
        vo.setPriceSuggested(suggested);
        vo.setVisionUsed(visionUsed);
        vo.setModel(ai == null ? "FALLBACK" : ai.model());
        vo.setTokens(ai == null ? 0 : ai.totalTokens());
        vo.setNote(note);
        return vo;
    }

    private static String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull() && hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
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

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private static String blankTo(String value, String fallback) {
        return hasText(value) ? value.trim() : (fallback == null ? "" : fallback.trim());
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static final class Draft {
        private String name;
        private String category;
        private String description;
        private java.math.BigDecimal price;
    }
}
