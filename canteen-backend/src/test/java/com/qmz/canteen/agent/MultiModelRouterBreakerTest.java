package com.qmz.canteen.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证「断网/服务商掉线时快速失败 + 熔断」：
 * 把硅基/智谱/scnet 全部指向一个本地必无可听服务的端口，让第一次调用快速失败（connect refused），
 * 连续失败到阈值后熔断器开闸（记录 skipUntil），随后的调用被直接跳过、不再发起网络请求 —— 全程远快于旧的逐个等 30s 超时。
 */
class MultiModelRouterBreakerTest {

    private static final String DEAD = "http://127.0.0.1:59999";

    private MultiModelRouter router;

    @BeforeEach
    void setUp() {
        AgentProperties zhipu = new AgentProperties();
        zhipu.setBaseUrl(DEAD);
        zhipu.setApiKey("sk-test-dead");

        AiRoutingProperties routing = new AiRoutingProperties();
        AiRoutingProperties.SiliconFlow sf = routing.getSiliconflow();
        sf.setBaseUrl(DEAD);
        sf.setApiKey("sk-test-dead");
        sf.setTimeoutMs(30000); // 故意给满 30s，验证熔断后不再等满

        AiRoutingProperties.Scnet sc = routing.getScnet();
        sc.setBaseUrl(DEAD);
        sc.setApiKey("sk-test-dead");
        sc.setDeepseekApiKey("sk-test-dead-deepseek");
        sc.setTimeoutMs(30000);
        routing.setFallbackToZhipu(true);

        router = new MultiModelRouter(zhipu, routing);
    }

    @Test
    void fastFailAndBreakerTripsAndSkips() throws Exception {
        // 1) 连续 3 次调用：每次硅基 connect-refused 秒挂 → 走正常代码路径累计失败
        long t0 = System.nanoTime();
        for (int i = 0; i < 3; i++) {
            assertThrows(IllegalStateException.class,
                    () -> router.complete(ModelRole.FAST, "s", "u", 100));
        }
        long threeCallsMs = (System.nanoTime() - t0) / 1_000_000;
        // 3 次完整降级链路（硅基→DeepSeek→GLM→智谱，全 refused 秒败）必须在 3s 内完成，绝不能等满 30s
        assertTrue(threeCallsMs < 3000, "3 次完整兜底应 <3s，实际 " + threeCallsMs + "ms");

        // 2) 断供商熔断器应已开闸：skipUntil > 当前时间
        Map<String, Object> breakers = breakerMap(router);
        assertNotNull(breakers.get("127.0.0.1"), "服务商熔断器应记录主机 127.0.0.1");
        AtomicInteger failures = (AtomicInteger) reflect(breakers.get("127.0.0.1"), "failures");
        long skipUntil = (long) reflect(breakers.get("127.0.0.1"), "skipUntil");
        assertEquals(0, failures.get(), "熔断开闸后失败计数应清零");
        assertTrue(skipUntil > System.currentTimeMillis(), "熔断应已生效（skipUntil>now）");

        // 3) 熔断生效：再调用一次，硅基被直接跳过（不再发网络请求），仍应在瞬间结束
        long t1 = System.nanoTime();
        assertThrows(IllegalStateException.class,
                () -> router.complete(ModelRole.FAST, "s", "u", 100));
        long skipCallMs = (System.nanoTime() - t1) / 1_000_000;
        assertTrue(skipCallMs < 1500, "熔断后调用应 <1.5s（硅基被跳过），实际 " + skipCallMs + "ms");

        // 4) 恢复路径：providerOk 清零 —— 手动清空熔断后需能重新放行（不抛「熔断中」）
        //    (通过再次清零 skipUntil 验证 providerOk 逻辑)
    }

    // ---------- 反射读取私有熔断状态（仅测试内部状态，不依赖网络） ----------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> breakerMap(MultiModelRouter r) throws Exception {
        Field f = r.getClass().getDeclaredField("providerBreakers");
        f.setAccessible(true);
        return (Map<String, Object>) f.get(r);
    }

    private static Object reflect(Object target, String field) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.get(target);
    }
}