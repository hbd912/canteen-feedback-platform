package com.qmz.canteen.service;

import com.qmz.canteen.vo.CustomerServiceMessageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 客服会话的 SSE 推送中心。
 * <p>
 * 每个会话维护一组订阅者（学生端 + 商家端），消息落库后立即推送；
 * 20 秒心跳一次，用于发现断开的连接并清理，避免 emitter 泄漏。
 */
@Slf4j
@Service
public class CustomerServiceStreamService {

    private final Map<Long, CopyOnWriteArrayList<Subscriber>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long sessionId) {
        SseEmitter emitter = new SseEmitter(0L);
        Subscriber sub = new Subscriber(emitter);
        subscribers.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(sub);

        emitter.onCompletion(() -> remove(sessionId, sub));
        emitter.onTimeout(() -> {
            remove(sessionId, sub);
            emitter.complete();
        });
        emitter.onError(e -> remove(sessionId, sub));

        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("sessionId", sessionId)));
            log.debug("SSE 订阅成功：session={} 当前订阅数={}", sessionId, subscribers.get(sessionId).size());
        } catch (IOException e) {
            remove(sessionId, sub);
        }
        return emitter;
    }

    /** 推送一条新消息给该会话的所有订阅者 */
    public void publish(Long sessionId, CustomerServiceMessageVO message) {
        List<Subscriber> list = subscribers.get(sessionId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Subscriber sub : list) {
            try {
                sub.emitter.send(SseEmitter.event().name("message").data(message));
            } catch (Exception e) {
                remove(sessionId, sub);
                try {
                    sub.emitter.complete();
                } catch (Exception ignored) {
                    // 已断开，忽略
                }
            }
        }
    }

    /** 心跳：既保活，也顺手清理已断开的连接 */
    @Scheduled(fixedDelay = 20_000)
    public void heartbeat() {
        for (Map.Entry<Long, CopyOnWriteArrayList<Subscriber>> entry : subscribers.entrySet()) {
            for (Subscriber sub : entry.getValue()) {
                try {
                    sub.emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    remove(entry.getKey(), sub);
                    try {
                        sub.emitter.complete();
                    } catch (Exception ignored) {
                        // 忽略
                    }
                }
            }
        }
    }

    public int onlineCount(Long sessionId) {
        List<Subscriber> list = subscribers.get(sessionId);
        return list == null ? 0 : list.size();
    }

    private void remove(Long sessionId, Subscriber sub) {
        CopyOnWriteArrayList<Subscriber> list = subscribers.get(sessionId);
        if (list != null) {
            list.remove(sub);
            if (list.isEmpty()) {
                subscribers.remove(sessionId);
            }
        }
    }

    private static final class Subscriber {
        private final SseEmitter emitter;

        private Subscriber(SseEmitter emitter) {
            this.emitter = emitter;
        }
    }
}
