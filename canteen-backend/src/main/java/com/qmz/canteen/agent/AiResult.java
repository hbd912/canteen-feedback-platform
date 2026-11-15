package com.qmz.canteen.agent;

/**
 * 一次模型调用的结果，带模型名与 token 用量，便于日志与成本观察。
 */
public record AiResult(String content, String model,
                       int promptTokens, int completionTokens, int totalTokens,
                       long costMs) {
}
