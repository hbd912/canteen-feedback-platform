package com.qmz.canteen.config;

import com.qmz.canteen.agent.AgentProperties;
import com.qmz.canteen.agent.LlmClient;
import com.qmz.canteen.agent.LlmProtocol;
import com.qmz.canteen.agent.ModelRole;
import com.qmz.canteen.agent.MultiModelRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * 模型冷启动预热 + 保活。
 * <p>
 * 慢的两个来源：
 * <ul>
 *   <li>本地懒加载：HttpClient 首次请求要建 TCP/TLS 连接（LlmClient 与 MultiModelRouter
 *       各有一个独立的懒加载 client，必须分别预热）。</li>
 *   <li>供应商侧冷加载：免费算力平台会把空闲几分钟的模型逐出显存，下次请求要重新加载权重，
 *       表现为「第一次慢、新建对话又慢」。保活 ping 让模型常驻，消除这一段。</li>
 * </ul>
 * 启动后预热一轮；之后每 {@link #KEEPALIVE_MS} 用极小请求（16 token）ping 一轮文本链路，
 * 每天流量约几千 token，远低于免费额度。全程 try/catch 静默。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Integer.MAX_VALUE)
public class ModelWarmup implements ApplicationRunner {

    private final MultiModelRouter router;
    private final LlmClient llmClient;
    private final AgentProperties agentProps;

    /** 保活间隔：3 分钟。多数免费平台空闲逐出窗口在 5~10 分钟，留足余量 */
    private static final long KEEPALIVE_MS = 180_000L;

    @Override
    public void run(ApplicationArguments args) {
        Thread t = new Thread(this::warmupThenKeepalive, "model-warmup");
        t.setDaemon(true);
        t.start();
    }

    private void warmupThenKeepalive() {
        warmup();
        while (true) {
            try {
                Thread.sleep(KEEPALIVE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            keepalive();
        }
    }

    /** 启动预热：四条链路各打一发，建立 TCP/TLS 并触发供应商侧加载 */
    private void warmup() {
        long start = System.currentTimeMillis();
        try {
            router.complete(ModelRole.FAST,
                    "你是校园食堂小助手，负责解答学生的点餐问题。",
                    "你好，请用一句话介绍一下你们档口的点餐方式。",
                    64, 0.2);
            log.info("FAST 模型预热完成：{}ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("FAST 模型预热失败（不影响启动，下次真实请求会自动冷加载）：{}", e.getMessage());
        }

        long brainStart = System.currentTimeMillis();
        try {
            router.complete(ModelRole.BRAIN,
                    "你是校园食堂档口的经营助理，回答简洁。",
                    "一句话：今天适合做什么经营动作？",
                    64, 0.2);
            log.info("BRAIN 模型预热完成：{}ms", System.currentTimeMillis() - brainStart);
        } catch (Exception e) {
            log.warn("BRAIN 模型预热失败（不影响启动）：{}", e.getMessage());
        }

        // 学生端 Agent 走 LlmClient（智谱 glm-4-flash），它有独立的懒加载 HttpClient，
        // 不预热的话第一条学生提问要多付一次 TCP/TLS 握手 + 供应商冷加载。
        long agentStart = System.currentTimeMillis();
        try {
            if (agentProps.isEnabled() && agentProps.getApiKey() != null && !agentProps.getApiKey().isBlank()) {
                LlmProtocol.ChatRequest req = new LlmProtocol.ChatRequest();
                req.setModel(agentProps.getModel());
                req.setMessages(java.util.List.of(
                        java.util.Map.of("role", "system", "content", "你是校园食堂小助手。"),
                        java.util.Map.of("role", "user", "content", "回复：好的")));
                req.setMaxTokens(16);
                req.setTemperature(0.2);
                llmClient.chat(req);
                log.info("学生端 Agent（智谱）预热完成：{}ms", System.currentTimeMillis() - agentStart);
            }
        } catch (Exception e) {
            log.warn("学生端 Agent 预热失败（不影响启动）：{}", e.getMessage());
        }

        long visionStart = System.currentTimeMillis();
        try {
            router.completeVision(
                    "你是菜品图片识别助手，回答一句话即可。",
                    "这张图大概是什么颜色？",
                    tinyImageDataUrl(), 64);
            log.info("VISION 模型预热完成：{}ms", System.currentTimeMillis() - visionStart);
        } catch (Exception e) {
            log.warn("VISION 模型预热失败（不影响启动，首次传图会自动冷加载）：{}", e.getMessage());
        }
    }

    /**
     * 保活 ping：FAST/BRAIN 各 8~16 token，再 ping 一次 LlmClient。
     * 失败静默——供应商限流/瞬断时不能把保活日志刷成噪音，也不能影响业务。
     */
    private void keepalive() {
        try {
            router.complete(ModelRole.FAST, "你是食堂小助手。", "ping", 8, 0.2);
        } catch (Exception ignored) {
        }
        try {
            router.complete(ModelRole.BRAIN, "你是经营助理。", "ping", 8, 0.2);
        } catch (Exception ignored) {
        }
        try {
            if (agentProps.isEnabled() && agentProps.getApiKey() != null && !agentProps.getApiKey().isBlank()) {
                LlmProtocol.ChatRequest req = new LlmProtocol.ChatRequest();
                req.setModel(agentProps.getModel());
                req.setMessages(java.util.List.of(java.util.Map.of("role", "user", "content", "ping")));
                req.setMaxTokens(8);
                req.setTemperature(0.2);
                llmClient.chat(req);
            }
        } catch (Exception ignored) {
        }
        log.debug("模型保活 ping 完成");
    }

    /** 64x64 暖色渐变 JPEG，用于触发视觉模型冷加载，不依赖任何本地文件 */
    private String tinyImageDataUrl() throws Exception {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        for (int y = 0; y < 64; y++) {
            g.setColor(new Color(230, 120 + y, 60 + y / 2));
            g.drawLine(0, y, 64, y);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
