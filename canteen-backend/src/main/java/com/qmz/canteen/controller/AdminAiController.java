package com.qmz.canteen.controller;

import com.qmz.canteen.agent.AgentProperties;
import com.qmz.canteen.agent.AiResult;
import com.qmz.canteen.agent.ModelRole;
import com.qmz.canteen.agent.MultiModelRouter;
import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.Result;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.entity.Notification;
import com.qmz.canteen.entity.Stall;
import com.qmz.canteen.mapper.StallMapper;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台管理 AI 助手：向商家发提醒、AI 生成公告文案等。
 */
@RestController
@RequestMapping("/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {

    private final NotificationService notificationService;
    private final MultiModelRouter router;
    private final StallMapper stallMapper;
    private final AgentProperties props;

    /**
     * 管理员向某档口商家发 AI 生成的提醒（如「学生反馈出餐慢，请尽快整改」）。
     * 返回实际发送的提醒文案。
     */
    @PostMapping("/merchant-notify")
    public Result<String> merchantNotify(@RequestParam Long stallId,
                                         @RequestParam(defaultValue = "") String topic) {
        UserContext.requireAdmin();
        Stall stall = stallMapper.selectById(stallId);
        if (stall == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "档口不存在");
        }
        String content = generateNotify(stall.getName(), topic);
        notificationService.notifyMerchant(stallId, Notification.TYPE_ADMIN_NOTICE,
                content, "/merchant/todos", null);
        return Result.success(content);
    }

    /**
     * 管理员 AI 生成公告（标题 + 正文）。
     */
    @PostMapping("/notice-generate")
    public Result<Map<String, String>> noticeGenerate(@RequestParam String topic) {
        UserContext.requireAdmin();
        String fallbackTitle = "校园食堂公告";
        String fallbackContent = "各位师生：关于「" + topic + "」的公告，请留意档口通知。";
        try {
            if (!props.isEnabled()) {
                return Result.success(Map.of("title", fallbackTitle, "content", fallbackContent));
            }
            String system = "你是校园食堂的管理员，需要根据主题写一则正式、得体的校园食堂公告。"
                    + "只输出一个 JSON 对象，不要任何解释：{\"title\":\"公告标题\",\"content\":\"公告正文\"}。"
                    + "标题简洁（10字内），正文 60~120 字，语气官方友好。";
            AiResult result = router.complete(ModelRole.FAST, system, "公告主题：" + topic, 400, 0.7);
            return Result.success(parseNotice(result.content(), fallbackTitle, fallbackContent));
        } catch (Exception e) {
            return Result.success(Map.of("title", fallbackTitle, "content", fallbackContent));
        }
    }

    private String generateNotify(String stallName, String topic) {
        String fallback = "【后台提醒】" + stallName + "：请及时查看并处理相关反馈" + (topic.isBlank() ? "" : "（" + topic + "）") + "。";
        try {
            if (!props.isEnabled()) {
                return fallback;
            }
            String system = "你是校园食堂的管理员，需要给档口「" + stallName + "」的商家发一条提醒。"
                    + "语气正式、礼貌但明确，指出问题并要求改进，60 字以内。只输出提醒正文。";
            AiResult result = router.complete(ModelRole.FAST, system,
                    "提醒主题：" + (topic.isBlank() ? "常规提醒" : topic), 120, 0.7);
            String content = result.content();
            return content == null || content.isBlank() ? fallback : clean(content);
        } catch (Exception e) {
            return fallback;
        }
    }

    private Map<String, String> parseNotice(String raw, String fallbackTitle, String fallbackContent) {
        try {
            int s = raw.indexOf('{');
            int e = raw.lastIndexOf('}');
            if (s < 0 || e <= s) {
                return Map.of("title", fallbackTitle, "content", fallbackContent);
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(raw.substring(s, e + 1));
            String title = node.path("title").asText(fallbackTitle);
            String content = node.path("content").asText(fallbackContent);
            Map<String, String> map = new LinkedHashMap<>();
            map.put("title", title);
            map.put("content", content);
            return map;
        } catch (Exception ex) {
            return Map.of("title", fallbackTitle, "content", fallbackContent);
        }
    }

    private String clean(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("`{1,3}([^`]*)`{1,3}", "$1")
                .replaceAll("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}]", "")
                .trim();
    }
}
