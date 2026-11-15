package com.qmz.canteen.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 多模型路由配置：硅基流动只承接「批量/难任务」，避免与智谱抢同一个限流池。
 */
@Data
@Component
@ConfigurationProperties(prefix = "canteen.ai")
public class AiRoutingProperties {

    private SiliconFlow siliconflow = new SiliconFlow();

    /** 算力平台（scnet）：GLM-5.3-Flash 1 亿 token，做视觉主力与文本兜底 */
    private Scnet scnet = new Scnet();

    /** 硅基失败时是否回落智谱 */
    private boolean fallbackToZhipu = true;

    @Data
    public static class SiliconFlow {
        private String baseUrl = "https://api.siliconflow.cn/v1";
        private String apiKey = "";
        private String fastModel = "Qwen/Qwen2.5-7B-Instruct";
        private String reasoningModel = "deepseek-ai/DeepSeek-R1-0528-Qwen3-8B";
        private String visionModel = "Qwen/Qwen3-VL-8B-Instruct";
        private String embeddingModel = "BAAI/bge-m3";
        private int timeoutMs = 30000;
        /** 向量化单独超时：正常 <1s，超时即降级关键词检索，不能拖住整次回答 */
        private int embeddingTimeoutMs = 4000;
        /** 视觉模型单独超时：正常 1~4 秒，偶发连接重置时尽快失败重试/降级 */
        private int visionTimeoutMs = 8000;
        /** 视觉备用模型（8B 失败时再试一次） */
        private String visionFallbackModel = "Qwen/Qwen3-VL-30B-A3B-Instruct";
    }

    /** 算力平台（scnet.cn，OpenAI 兼容） */
    @Data
    public static class Scnet {
        private String baseUrl = "https://api.scnet.cn/api/llm/v1";
        private String apiKey = "";
        /** 1 亿 token 的 GLM-5.3-Flash：支持看图/工具，可关闭长思考 */
        private String glmModel = "GLM-5.3-Flash";
        /** 5000 万 token 的 DeepSeek：工具/报告主力，默认关思考 */
        private String deepseekApiKey = "";
        private String deepseekModel = "DeepSeek-V4.1-Flash-1017-Event";
        private int timeoutMs = 30000;
        /** 视觉正常 3~6 秒，留足余量 */
        private int visionTimeoutMs = 10000;
        /** DeepSeek 视觉较慢（5~11 秒），单独超时 */
        private int deepseekVisionTimeoutMs = 12000;
        /** 默认关闭 GLM 长思考（FAST/BRAIN/视觉），只有深度分析才开 */
        private boolean disableThinking = true;
    }
}
