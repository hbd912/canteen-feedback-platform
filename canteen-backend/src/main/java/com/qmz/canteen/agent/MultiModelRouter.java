package com.qmz.canteen.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多模型路由：按任务性质选模型，并在失败时自动降级。
 * <p>
 * 分工原则（为了不把同一个免费额度压爆）：
 * <ul>
 *   <li>BRAIN（大脑判断）→ 智谱 glm-4-flash，稳定、支持工具调用</li>
 *   <li>FAST（客服/评价/经营问答）→ 硅基 Qwen2.5-7B，快、免费</li>
 *   <li>REASONING（代码/诊断/复杂纠纷）→ 硅基 DeepSeek-R1-8B，只在难任务时调用</li>
 * </ul>
 * 任一模型失败都会自动尝试另一家，保证免费额度被限流时不中断业务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiModelRouter {

    private final AgentProperties zhipu;
    private final AiRoutingProperties routing;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private volatile HttpClient httpClient;

    /** 视觉模型熔断：连续失败 2 次后暂停 2 分钟，避免每次都把超时等满 */
    private final Map<String, Breaker> visionBreakers = new ConcurrentHashMap<>();

    /** 文本模型服务商熔断：按服务商 base-url 主机维度计，连续失败后开闸，避免断网时逐个傻等超时 */
    private final Map<String, Breaker> providerBreakers = new ConcurrentHashMap<>();
    private static final int BREAKER_THRESHOLD = 3;      // 连续失败 N 次
    private static final long BREAK_MS = 45_000L;        // 熔断时长 45 秒
    /** 网络瞬断时单次请求绝不超过该值，保证降级链能快速失败 */
    private static final long TEXT_CALL_HARD_CAP_MS = 10_000L;

    private static final class Breaker {
        private final AtomicInteger failures = new AtomicInteger();
        private volatile long skipUntil = 0L;
    }

    /**
     * 发起一次纯文本补全。
     *
     * @param role      任务角色
     * @param system    系统提示词
     * @param user      用户内容
     * @param maxTokens 期望的最大输出 token（内部会再做一次上限保护）
     */
    public AiResult complete(ModelRole role, String system, String user, int maxTokens) {
        // 默认温度：决策/推理用 0.2 保证稳定，客服/评价话术可调用重载方法调高温度增加多样性
        return complete(role, system, user, maxTokens, 0.2);
    }

    /**
     * 带温度的补全。客服/评价等「需要口语化、避免千篇一律」的任务用较高温度（0.8~0.9）；
     * 大脑决策/推理任务保持低温（0.2）避免输出不稳定。
     */
    public AiResult complete(ModelRole role, String system, String user, int maxTokens, double temperature) {
        if (role == ModelRole.VISION) {
            throw new IllegalArgumentException("视觉任务请调用 completeVision");
        }
        // 上限 2000：允许模型把分析写透（此前 1200 导致长回答被拦腰截断）
        int cap = Math.min(Math.max(maxTokens, 64), 2000);
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", system == null ? "" : system),
                Map.of("role", "user", "content", user == null ? "" : user)
        );

        if (role == ModelRole.BRAIN) {
            // 大脑：DeepSeek 判断更强（工具调用 2.5s）→ 智谱（快）→ GLM → 硅基
            if (hasDeepseek()) {
                try {
                    return callDeepseek(messages, cap, routing.getScnet().getDeepseekModel(),
                            temperature, false);
                } catch (Exception e) {
                    log.warn("大脑调用 DeepSeek 失败，尝试智谱兜底：{}", e.getMessage());
                }
            }
            try {
                return callZhipu(messages, cap, temperature);
            } catch (Exception e) {
                log.warn("大脑调用智谱失败，尝试 GLM 兜底：{}", e.getMessage());
            }
            if (hasScnet()) {
                try {
                    return callScnet(messages, cap, routing.getScnet().getGlmModel(), temperature, true);
                } catch (Exception e) {
                    log.warn("大脑调用 GLM 失败，尝试硅基兜底：{}", e.getMessage());
                }
            }
            if (hasSiliconFlow()) {
                return callSilicon(messages, cap, routing.getSiliconflow().getFastModel(), temperature);
            }
            throw new IllegalStateException("大脑模型不可用：DeepSeek、智谱、GLM 与硅基均未配置或均调用失败");
        }

        if (role == ModelRole.REPORT) {
            // 经营报告：DeepSeek 质量更好 → GLM 1 亿 token → 硅基/智谱
            if (hasDeepseek()) {
                try {
                    return callDeepseek(messages, Math.max(cap, 600), routing.getScnet().getDeepseekModel(),
                            temperature, false);
                } catch (Exception e) {
                    log.warn("报告调用 DeepSeek 失败，尝试 GLM 兜底：{}", e.getMessage());
                }
            }
            if (hasScnet()) {
                try {
                    return callScnet(messages, cap, routing.getScnet().getGlmModel(), temperature, true);
                } catch (Exception e) {
                    log.warn("报告调用 GLM 失败，尝试硅基兜底：{}", e.getMessage());
                }
            }
            if (hasSiliconFlow()) {
                try {
                    return callSilicon(messages, cap, routing.getSiliconflow().getFastModel(), temperature);
                } catch (Exception e) {
                    log.warn("报告调用硅基失败，尝试智谱兜底：{}", e.getMessage());
                }
            }
            return callZhipu(messages, cap, temperature);
        }

        // FAST / REASONING：硅基 Qwen 最快；失败后 DeepSeek（更有人味）→ GLM → 智谱
        if (hasSiliconFlow() && role == ModelRole.FAST) {
            try {
                return callSilicon(messages, cap, routing.getSiliconflow().getFastModel(), temperature);
            } catch (Exception e) {
                log.warn("硅基 FAST 调用失败，尝试 DeepSeek 兜底：{}", e.getMessage());
            }
        }
        if (hasDeepseek()) {
            try {
                // REASONING 允许 DeepSeek 思考；FAST 关闭思考保证速度
                boolean noThink = role != ModelRole.REASONING;
                int roleCap = role == ModelRole.REASONING ? Math.max(cap, 800) : cap;
                return callDeepseek(messages, roleCap, routing.getScnet().getDeepseekModel(),
                        temperature, !noThink);
            } catch (Exception e) {
                log.warn("DeepSeek 调用失败，尝试 GLM 兜底：{}", e.getMessage());
            }
        }
        if (role == ModelRole.REASONING && hasSiliconFlow()) {
            try {
                return callSilicon(messages, Math.max(cap, 600),
                        routing.getSiliconflow().getReasoningModel(), temperature);
            } catch (Exception e) {
                log.warn("硅基推理模型调用失败，尝试 GLM 兜底：{}", e.getMessage());
            }
        }
        if (hasScnet()) {
            try {
                boolean noThink = role != ModelRole.REASONING;
                return callScnet(messages, cap, routing.getScnet().getGlmModel(), temperature, noThink);
            } catch (Exception e) {
                log.warn("GLM 调用失败，尝试智谱兜底：{}", e.getMessage());
            }
        }
        if (routing.isFallbackToZhipu()) {
            return callZhipu(messages, cap, temperature);
        }
        throw new IllegalStateException("文本模型均调用失败");
    }

    public boolean hasSiliconFlow() {
        String key = routing.getSiliconflow().getApiKey();
        return key != null && !key.isBlank();
    }

    public boolean hasScnet() {
        String key = routing.getScnet().getApiKey();
        return key != null && !key.isBlank();
    }

    public boolean hasDeepseek() {
        String key = routing.getScnet().getDeepseekApiKey();
        return key != null && !key.isBlank();
    }

    /**
     * 视觉理解：GLM-5.3-Flash（scnet）主力 → Qwen3-VL-8B → Qwen3-VL-30B 兜底。
     * 全部失败时抛错，由上层给出明确提示；连续失败的模型熔断 2 分钟，避免每次都等满超时。
     */
    public AiResult completeVision(String system, String user, String imageDataUrl, int maxTokens) {
        if (imageDataUrl == null || imageDataUrl.isBlank()) {
            throw new IllegalArgumentException("图片数据为空");
        }
        int cap = Math.min(Math.max(maxTokens, 128), 800);
        // GLM 不接受空文本块（会返回 400 invalid parameters），只传图时补一句默认说明
        String userText = (user == null || user.isBlank()) ? "请描述这张图片中的菜品。" : user;
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", system == null ? "" : system),
                Map.of("role", "user", "content", List.of(
                        Map.of("type", "text", "text", userText),
                        Map.of("type", "image_url", "image_url", Map.of("url", imageDataUrl))
                ))
        );
        List<String> errors = new ArrayList<>();

        // 1) GLM-5.3-Flash：1 亿 token、看图稳定、可关思考
        if (hasScnet()) {
            AiRoutingProperties.Scnet sc = routing.getScnet();
            String key = "scnet/glm";
            if (visionSkipped(key)) {
                log.warn("GLM 视觉熔断中，跳过");
            } else {
                try {
                    AiResult r = callScnet(messages, cap, sc.getGlmModel(), 0.4, true, sc.getVisionTimeoutMs());
                    visionOk(key);
                    return r;
                } catch (Exception e) {
                    visionFailed(key);
                    errors.add("GLM: " + e.getMessage());
                    log.warn("GLM 视觉失败，降级 Qwen：{}", e.getMessage());
                }
            }
        }

        // 2) DeepSeek-V4.1-Flash：看图 5~11 秒，稳定性好，做第二兜底
        if (hasDeepseek()) {
            AiRoutingProperties.Scnet sc = routing.getScnet();
            String key = "scnet/deepseek";
            if (visionSkipped(key)) {
                log.warn("DeepSeek 视觉熔断中，跳过");
            } else {
                try {
                    AiResult r = callDeepseek(messages, cap, sc.getDeepseekModel(), 0.4,
                            false, sc.getDeepseekVisionTimeoutMs());
                    visionOk(key);
                    return r;
                } catch (Exception e) {
                    visionFailed(key);
                    errors.add("DeepSeek: " + e.getMessage());
                    log.warn("DeepSeek 视觉失败，降级 Qwen：{}", e.getMessage());
                }
            }
        }

        // 3) 硅基 Qwen3-VL-8B（快，但偶发超时/重置）
        if (hasSiliconFlow()) {
            AiRoutingProperties.SiliconFlow sf = routing.getSiliconflow();
            String key = "sf/" + sf.getVisionModel();
            if (!visionSkipped(key)) {
                try {
                    AiResult r = call(sf.getBaseUrl(), sf.getApiKey(), sf.getVisionModel(),
                            messages, cap, visionTimeout(sf), 0.4);
                    visionOk(key);
                    return r;
                } catch (Exception e) {
                    visionFailed(key);
                    errors.add("Qwen8B: " + e.getMessage());
                    log.warn("Qwen3-VL-8B 视觉失败，尝试 30B 兜底：{}", e.getMessage());
                }
            } else {
                log.warn("Qwen3-VL-8B 视觉熔断中，跳过");
            }

            // 4) 硅基 Qwen3-VL-30B-A3B 最后兜底
            String fallback = sf.getVisionFallbackModel();
            if (fallback != null && !fallback.isBlank() && !fallback.equals(sf.getVisionModel())) {
                String key2 = "sf/" + fallback;
                if (!visionSkipped(key2)) {
                    try {
                        AiResult r = call(sf.getBaseUrl(), sf.getApiKey(), fallback,
                                messages, cap, visionTimeout(sf), 0.4);
                        visionOk(key2);
                        return r;
                    } catch (Exception e) {
                        visionFailed(key2);
                        errors.add("Qwen30B: " + e.getMessage());
                    }
                }
            }
        }

        // 所有模型都在熔断里时不能直接判死刑：重置熔断，硬试一次 GLM，避免整段时间完全不可用
        if (errors.isEmpty() && hasScnet()) {
            log.warn("视觉模型全部处于熔断，重置后直接重试 GLM");
            visionBreakers.clear();
            try {
                AiResult r = callScnet(messages, cap, routing.getScnet().getGlmModel(), 0.4, true,
                        routing.getScnet().getVisionTimeoutMs());
                visionOk("scnet/glm");
                return r;
            } catch (Exception e) {
                errors.add("GLM: " + e.getMessage());
            }
        }

        throw new IllegalStateException("视觉模型均不可用：" + String.join("；", errors));
    }

    private int visionTimeout(AiRoutingProperties.SiliconFlow sf) {
        return sf.getVisionTimeoutMs() > 0 ? sf.getVisionTimeoutMs() : sf.getTimeoutMs();
    }

    private boolean visionSkipped(String key) {
        Breaker b = visionBreakers.get(key);
        return b != null && System.currentTimeMillis() < b.skipUntil;
    }

    /** 按 base-url 主机归一化服务商 key，熔断以「主机」为单位，而不是每个模型 */
    private static String providerKey(String baseUrl) {
        try {
            return URI.create(baseUrl).getHost() == null ? baseUrl : URI.create(baseUrl).getHost();
        } catch (Exception e) {
            return baseUrl;
        }
    }

    private boolean providerSkipped(String key) {
        Breaker b = providerBreakers.get(key);
        return b != null && System.currentTimeMillis() < b.skipUntil;
    }

    private void providerFailed(String key) {
        Breaker b = providerBreakers.computeIfAbsent(key, k -> new Breaker());
        if (b.failures.incrementAndGet() >= BREAKER_THRESHOLD) {
            b.failures.set(0);
            b.skipUntil = System.currentTimeMillis() + BREAK_MS;
            log.warn("服务商 {} 连续失败，熔断 {} 秒", key, BREAK_MS / 1000);
        }
    }

    private void providerOk(String key) {
        Breaker b = providerBreakers.get(key);
        if (b != null) {
            b.failures.set(0);
            b.skipUntil = 0L;
        }
    }

    private void visionFailed(String key) {
        Breaker b = visionBreakers.computeIfAbsent(key, k -> new Breaker());
        // 阈值 3 次、熔断 60 秒：网络瞬断时不要因为两次失败就把模型锁死太久
        if (b.failures.incrementAndGet() >= 3) {
            b.failures.set(0);
            b.skipUntil = System.currentTimeMillis() + 60_000L;
            log.warn("视觉模型 {} 连续失败，熔断 60 秒", key);
        }
    }

    private void visionOk(String key) {
        Breaker b = visionBreakers.get(key);
        if (b != null) {
            b.failures.set(0);
            b.skipUntil = 0L;
        }
    }

    private AiResult callZhipu(List<Map<String, Object>> messages, int maxTokens, double temperature) {
        checkKey(zhipu.getApiKey(), "智谱");
        return call(zhipu.getBaseUrl(), zhipu.getApiKey(), zhipu.getModel(),
                messages, maxTokens, zhipu.getTimeoutMs(), temperature);
    }

    private AiResult callSilicon(List<Map<String, Object>> messages, int maxTokens, String model, double temperature) {
        AiRoutingProperties.SiliconFlow sf = routing.getSiliconflow();
        checkKey(sf.getApiKey(), "硅基流动");
        return call(sf.getBaseUrl(), sf.getApiKey(), model, messages, maxTokens, sf.getTimeoutMs(), temperature);
    }

    /**
     * DeepSeek-V4.1-Flash（scnet，独立 key）。它默认开思考且会吃光 max_tokens，
     * 所以这里强制显式传 enable_thinking：非推理任务一律 false。
     */
    private AiResult callDeepseek(List<Map<String, Object>> messages, int maxTokens, String model,
                                  double temperature, boolean enableThinking) {
        AiRoutingProperties.Scnet sc = routing.getScnet();
        checkKey(sc.getDeepseekApiKey(), "DeepSeek");
        Map<String, Object> extra = Map.of("enable_thinking", enableThinking);
        return call(sc.getBaseUrl(), sc.getDeepseekApiKey(), model, messages, maxTokens,
                sc.getTimeoutMs(), temperature, extra);
    }

    private AiResult callDeepseek(List<Map<String, Object>> messages, int maxTokens, String model,
                                  double temperature, boolean enableThinking, int timeoutMs) {
        AiRoutingProperties.Scnet sc = routing.getScnet();
        checkKey(sc.getDeepseekApiKey(), "DeepSeek");
        Map<String, Object> extra = Map.of("enable_thinking", enableThinking);
        return call(sc.getBaseUrl(), sc.getDeepseekApiKey(), model, messages, maxTokens,
                timeoutMs, temperature, extra);
    }

    /** scnet（OpenAI 兼容）。disableThinking=true 时关闭 GLM 长思考，响应可从 6~10 秒降到 2 秒左右。 */
    private AiResult callScnet(List<Map<String, Object>> messages, int maxTokens, String model,
                               double temperature, boolean disableThinking) {
        AiRoutingProperties.Scnet sc = routing.getScnet();
        return callScnet(messages, maxTokens, model, temperature, disableThinking, sc.getTimeoutMs());
    }

    private AiResult callScnet(List<Map<String, Object>> messages, int maxTokens, String model,
                               double temperature, boolean disableThinking, int timeoutMs) {
        AiRoutingProperties.Scnet sc = routing.getScnet();
        checkKey(sc.getApiKey(), "scnet");
        Map<String, Object> extra = (disableThinking && sc.isDisableThinking())
                ? Map.of("thinking", Map.of("type", "disabled"))
                : Map.of();
        return call(sc.getBaseUrl(), sc.getApiKey(), model, messages, maxTokens, timeoutMs, temperature, extra);
    }

    private AiResult call(String baseUrl, String apiKey, String model,
                          List<Map<String, Object>> messages, int maxTokens, int timeoutMs, double temperature) {
        return call(baseUrl, apiKey, model, messages, maxTokens, timeoutMs, temperature, Map.of());
    }

    private AiResult call(String baseUrl, String apiKey, String model,
                          List<Map<String, Object>> messages, int maxTokens, int timeoutMs,
                          double temperature, Map<String, Object> extra) {
        String endpoint = trimSlash(baseUrl) + "/chat/completions";
        // 服务商维度熔断：断网时不再逐模型傻等超时，挂掉的就直接跳过、快速失败
        String providerKey = providerKey(baseUrl);
        if (providerSkipped(providerKey)) {
            throw new IllegalStateException("服务商 " + providerKey + " 熔断中，跳过");
        }
        long effectiveTimeout = Math.min(timeoutMs, TEXT_CALL_HARD_CAP_MS);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        if (extra != null && !extra.isEmpty()) {
            body.putAll(extra);
        }

        String json;
        try {
            json = MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("序列化模型请求失败：" + e.getMessage(), e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMillis(effectiveTimeout))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        long start = System.currentTimeMillis();
        HttpResponse<String> response;
        try {
            response = client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            providerFailed(providerKey);
            throw new IllegalStateException("模型调用失败（" + model + "）：" + e.getMessage(), e);
        }
        long cost = System.currentTimeMillis() - start;
        String raw = response.body() == null ? "" : response.body();

        if (response.statusCode() != 200) {
            providerFailed(providerKey);
            throw new IllegalStateException("模型 " + model + " 返回 HTTP " + response.statusCode()
                    + "：" + abbreviate(raw, 200));
        }
        try {
            JsonNode root = MAPPER.readTree(raw);
            if (root.has("error") && !root.get("error").isNull()) {
                providerFailed(providerKey);
                throw new IllegalStateException("模型 " + model + " 返回错误：" + abbreviate(root.get("error").toString(), 200));
            }
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                throw new IllegalStateException("模型 " + model + " 未返回候选结果");
            }
            JsonNode message = choices.get(0).get("message");
            String content = message == null ? "" : text(message.get("content"));
            if (content.isBlank()) {
                // 推理模型可能把内容全放在 reasoning_content，说明 max_tokens 不够
                String reasoning = message == null ? "" : text(message.get("reasoning_content"));
                if (!reasoning.isBlank()) {
                    throw new IllegalStateException("模型 " + model + " 只输出了思考过程，答案被截断");
                }
                throw new IllegalStateException("模型 " + model + " 返回空内容");
            }
            JsonNode usage = root.get("usage");
            int prompt = usage == null ? 0 : usage.path("prompt_tokens").asInt(0);
            int completion = usage == null ? 0 : usage.path("completion_tokens").asInt(0);
            int total = usage == null ? 0 : usage.path("total_tokens").asInt(prompt + completion);
            log.info("模型调用完成：{} 耗时={}ms tokens={}", model, cost, total);
            providerOk(providerKey);
            return new AiResult(content.trim(), model, prompt, completion, total, cost);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析模型 " + model + " 响应失败：" + e.getMessage(), e);
        }
    }

    private HttpClient client() {
        HttpClient current = httpClient;
        if (current == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofMillis(8000))
                            .build();
                }
            }
            current = httpClient;
        }
        return current;
    }

    private static void checkKey(String key, String provider) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(provider + " API Key 未配置");
        }
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
