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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文本向量：调用硅基流动 OpenAI 兼容的 /embeddings（bge-m3，免费）。
 * 只负责「文本 → 向量」，检索逻辑在 ExperienceService。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingClient {

    private final AiRoutingProperties routing;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private volatile HttpClient httpClient;

    /**
     * 请求级缓存：同一段文本的向量永远相同，重复提问（重试/刷新/相似问候语）不必再打一次网络。
     * LRU 简化实现：达到上限直接清空——真实场景里 entry 很小（1024 float），512 条 ≈ 2MB。
     */
    private static final int CACHE_MAX = 512;
    private final Map<String, float[]> cache = new java.util.concurrent.ConcurrentHashMap<>();

    public String model() {
        return routing.getSiliconflow().getEmbeddingModel();
    }

    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("待向量化文本为空");
        }
        String key = model() + "|" + text;
        float[] cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        float[] result = embedRemote(text);
        if (cache.size() >= CACHE_MAX) {
            cache.clear();
        }
        cache.put(key, result);
        return result;
    }

    private float[] embedRemote(String text) {
        AiRoutingProperties.SiliconFlow sf = routing.getSiliconflow();
        if (sf.getApiKey() == null || sf.getApiKey().isBlank()) {
            throw new IllegalStateException("硅基流动未配置，无法向量化");
        }
        String input = text.length() <= 2000 ? text : text.substring(0, 2000);
        // 向量化是每次提问的前置串行步骤，超时必须收紧：4 秒拿不到就降级关键词检索，
        // 不能让一次 30s 的网络挂起拖住整次回答
        int timeoutMs = sf.getEmbeddingTimeoutMs() > 0 ? sf.getEmbeddingTimeoutMs() : sf.getTimeoutMs();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", sf.getEmbeddingModel());
        body.put("input", List.of(input));

        String json;
        try {
            json = MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("序列化向量请求失败：" + e.getMessage(), e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(sf.getBaseUrl()) + "/embeddings"))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Bearer " + sf.getApiKey())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException e) {
            throw new IllegalStateException("向量化超时（" + timeoutMs + "ms）", e);
        } catch (Exception e) {
            throw new IllegalStateException("向量化调用失败：" + e.getMessage(), e);
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("向量化返回 HTTP " + response.statusCode()
                    + "：" + abbreviate(response.body(), 200));
        }
        try {
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode data = root.get("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                throw new IllegalStateException("向量化未返回数据");
            }
            JsonNode vector = data.get(0).get("embedding");
            if (vector == null || !vector.isArray() || vector.isEmpty()) {
                throw new IllegalStateException("向量化返回空向量");
            }
            float[] result = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                result[i] = (float) vector.get(i).asDouble();
            }
            return result;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析向量响应失败：" + e.getMessage(), e);
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
