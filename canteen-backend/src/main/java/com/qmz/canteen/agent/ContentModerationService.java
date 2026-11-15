package com.qmz.canteen.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qmz.canteen.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 内容安全审核：先走本地敏感词库（确定性、零成本、秒回），
 * 再用 DeepSeek（BRAIN 路由）做语义审核，并生成给学生的温和提醒话术。
 * 模型不可用时一律「放行」，绝不影响用户正常提交。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentModerationService {

    private final MultiModelRouter router;
    private final AgentProperties props;
    private final SysConfigService sysConfigService;
    private final PromptProperties prompts;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 本地敏感词库（命中即拦截，不调模型） */
    private static final String[] BLOCK_WORDS = {
            "傻逼", "傻b", "煞笔", "cnm", "操你", "草你", "妈的", "他妈", "你妈",
            "去死", "滚蛋", "脑残", "弱智", "白痴", "智障", "死全家", "王八蛋",
            "贱人", "婊子", "狗东西", "日你", "狗日", "fuck", "shit", "bitch", "nmsl"
    };

    public ModerationResult check(String content) {
        // 自动屏蔽开关关闭时直接放行
        if (!sysConfigService.isModerationAutoBlock()) {
            return ModerationResult.pass();
        }
        if (content == null || content.isBlank()) {
            return ModerationResult.pass();
        }
        String text = content.trim();
        String lower = text.toLowerCase();

        // 1. 本地敏感词
        for (String w : BLOCK_WORDS) {
            if (lower.contains(w)) {
                return ModerationResult.block("辱骂", "包含不当用语", generateStudentReply("辱骂", "包含不当用语"));
            }
        }

        // 2. DeepSeek 语义审核 + 学生话术
        if (!props.isEnabled()) {
            return ModerationResult.pass();
        }
        try {
            AiResult result = router.complete(ModelRole.BRAIN, prompts.getModeration(),
                    "待审核内容：" + text, 120, 0.0);
            ModerationResult parsed = parse(result.content());
            if (parsed.flagged() && parsed.studentReply() == null) {
                // 模型没给话术就补一句
                return ModerationResult.block(parsed.category(), parsed.reason(),
                        generateStudentReply(parsed.category(), parsed.reason()));
            }
            return parsed;
        } catch (Exception e) {
            log.warn("内容审核模型调用失败，放行：{}", e.getMessage());
            return ModerationResult.pass();
        }
    }

    /** 让 DeepSeek 生成给学生的话术 */
    private String generateStudentReply(String category, String reason) {
        try {
            if (!props.isEnabled()) {
                return null;
            }
            String user = "违规类别：" + category + "，理由：" + reason;
            AiResult result = router.complete(ModelRole.BRAIN, prompts.getModerationReply(), user, 80, 0.4);
            String text = result.content();
            if (text != null && !text.isBlank()) {
                return text.replaceAll("\\s+", " ").trim();
            }
        } catch (Exception e) {
            log.warn("生成学生提醒话术失败：{}", e.getMessage());
        }
        return "你的发言因包含不当内容已被系统屏蔽，请修改后重新发布。";
    }

    private ModerationResult parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ModerationResult.pass();
        }
        try {
            String json = raw;
            int s = raw.indexOf('{');
            int e = raw.lastIndexOf('}');
            if (s >= 0 && e > s) {
                json = raw.substring(s, e + 1);
            }
            JsonNode node = MAPPER.readTree(json);
            boolean flagged = node.path("flagged").asBoolean(false);
            if (!flagged) {
                return ModerationResult.pass();
            }
            String category = node.path("category").asText("违规");
            String reason = node.path("reason").asText("内容违规");
            return ModerationResult.block(category, reason, null);
        } catch (Exception ex) {
            return ModerationResult.pass();
        }
    }
}
