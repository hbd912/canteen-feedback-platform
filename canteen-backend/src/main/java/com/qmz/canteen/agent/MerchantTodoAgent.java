package com.qmz.canteen.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qmz.canteen.entity.CustomerServiceSession;
import com.qmz.canteen.entity.DiningOrder;
import com.qmz.canteen.entity.Dish;
import com.qmz.canteen.entity.DishReview;
import com.qmz.canteen.entity.RefundRequest;
import com.qmz.canteen.entity.Stall;
import com.qmz.canteen.mapper.CustomerServiceSessionMapper;
import com.qmz.canteen.mapper.DishMapper;
import com.qmz.canteen.mapper.DishReviewMapper;
import com.qmz.canteen.mapper.OrderMapper;
import com.qmz.canteen.mapper.RefundRequestMapper;
import com.qmz.canteen.mapper.StallMapper;
import com.qmz.canteen.security.LoginUser;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.CustomerServiceService;
import com.qmz.canteen.service.OrderService;
import com.qmz.canteen.service.ReviewService;
import com.qmz.canteen.vo.AiProcessReportVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 商家「AI 一键代理」：自动处理本档口待办事项。
 * <p>
 * 处理范围（确定性执行 + LLM 生成内容）：
 * <ul>
 *   <li>新订单 → 一键出餐完成（DONE），并通知学生取餐；</li>
 *   <li>客服未读 → 用 {@link MerchantReplyGenerator} 生成真人语气回复并发送；</li>
 *   <li>待回复评价 → 按星级/内容生成回复并发送；</li>
 *   <li>退款 → 涉及金钱，跳过留待人工审核。</li>
 * </ul>
 * 每项处理相互独立，单项失败不影响其它项，最终返回一份处理报告。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantTodoAgent {

    private final OrderMapper orderMapper;
    private final DishMapper dishMapper;
    private final DishReviewMapper dishReviewMapper;
    private final RefundRequestMapper refundRequestMapper;
    private final CustomerServiceSessionMapper sessionMapper;
    private final OrderService orderService;
    private final ReviewService reviewService;
    private final CustomerServiceService customerServiceService;
    private final MerchantReplyGenerator replyGenerator;
    private final MultiModelRouter router;
    private final StallMapper stallMapper;

    /** 每档口独立：后台线程池跑 AI 一键处理，不让 HTTP 同步阻塞超时 */
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "merchant-todo-ai");
        t.setDaemon(true);
        return t;
    });

    /** 档口 -> 该档口最近一次 AI 处理的状态（在跑/完成/失败 + 报告 + 完成时间） */
    private final Map<Long, AiJobState> jobStates = new ConcurrentHashMap<>();

    /** 后台异步跑 {code process}，当前状态用 {@link #status} 轮询。调用线程身份先复刻到工作线程。 */
    public void processAsync(Long stallId, LoginUser loginUser) {
        jobStates.computeIfAbsent(stallId, k -> new AiJobState());
        jobStates.get(stallId).started();
        executor.execute(() -> {
            UserContext.set(loginUser); // sendAiReply 内部要读 requireMerchant/getUserId，复制身份到工作线程
            try {
                AiProcessReportVO report = process(stallId);
                jobStates.get(stallId).finished(report);
            } catch (Exception e) {
                log.warn("AI 一键代理处理档口 {} 失败：{}", stallId, e.getMessage());
                jobStates.get(stallId).failed(e.getMessage());
            } finally {
                UserContext.clear();
            }
        });
    }

    /** 当前是否有某个档口正在 AI 处理 */
    public boolean isRunning(Long stallId) {
        return jobStates.containsKey(stallId) && jobStates.get(stallId).running;
    }

    /** 档口最近一次 AI 处理状态（无记录则返回空状态） */
    public AiJobState status(Long stallId) {
        return jobStates.getOrDefault(stallId, new AiJobState());
    }

    /** 某档口的 AI 处理任务状态（供接口轮询） */
    public static final class AiJobState {
        private final Object lock = new Object();
        private volatile boolean running;
        private volatile AiProcessReportVO report;
        private volatile String error;
        private volatile LocalDateTime doneAt;

        void started() {
            synchronized (lock) {
                running = true;
                report = null;
                error = null;
                doneAt = null;
            }
        }

        void finished(AiProcessReportVO r) {
            synchronized (lock) {
                running = false;
                report = r;
                error = null;
                doneAt = LocalDateTime.now();
            }
        }

        void failed(String msg) {
            synchronized (lock) {
                running = false;
                report = null;
                error = msg;
                doneAt = LocalDateTime.now();
            }
        }

        public boolean isRunning() {
            return running;
        }

        public AiProcessReportVO getReport() {
            return report;
        }

        public String getError() {
            return error;
        }

        public LocalDateTime getDoneAt() {
            return doneAt;
        }
    }

    public AiProcessReportVO process(Long stallId) {
        AiProcessReportVO report = new AiProcessReportVO();

        // 1. 新订单（待取餐 + 已支付）→ 一键出餐完成
        List<DiningOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<DiningOrder>()
                .eq(DiningOrder::getStallId, stallId)
                .eq(DiningOrder::getStatus, "PENDING")
                .eq(DiningOrder::getPayStatus, "PAID"));
        for (DiningOrder o : orders) {
            try {
                orderService.updateStatus(o.getId(), "DONE", null, false, stallId);
                report.setOrderDone(report.getOrderDone() + 1);
            } catch (Exception e) {
                log.warn("AI 代理完成订单 {} 失败：{}", o.getId(), e.getMessage());
            }
        }

        // 2. 客服未读 → AI 回复（真人语气，不沉淀为人工经验）
        List<CustomerServiceSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<CustomerServiceSession>()
                .eq(CustomerServiceSession::getStallId, stallId)
                .gt(CustomerServiceSession::getMerchantUnread, 0));
        for (CustomerServiceSession s : sessions) {
            try {
                if (customerServiceService.sendAiReply(s.getId())) {
                    report.setCsReplied(report.getCsReplied() + 1);
                }
            } catch (Exception e) {
                log.warn("AI 代理回复客服会话 {} 失败：{}", s.getId(), e.getMessage());
            }
        }

        // 3. 待回复评价 → AI 回复
        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getStallId, stallId));
        List<Long> dishIds = dishes.stream().map(Dish::getId).toList();
        if (!dishIds.isEmpty()) {
            List<DishReview> reviews = dishReviewMapper.selectList(new LambdaQueryWrapper<DishReview>()
                    .in(DishReview::getDishId, dishIds)
                    .eq(DishReview::getStatus, DishReview.STATUS_PUBLISHED)
                    .eq(DishReview::getHandled, 0));
            for (DishReview r : reviews) {
                try {
                    String reply = replyGenerator.generateReviewReply(r.getId(), stallId);
                    reviewService.replyReview(r.getId(), stallId, reply, "AI");
                    report.setReviewReplied(report.getReviewReplied() + 1);
                } catch (Exception e) {
                    log.warn("AI 代理回复评价 {} 失败：{}", r.getId(), e.getMessage());
                }
            }
        }

        // 4. 退款 → 跳过（涉及金钱需人工）
        Long refundCount = refundRequestMapper.selectCount(new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getStallId, stallId)
                .eq(RefundRequest::getStatus, RefundRequest.STATUS_PENDING));
        report.setRefundSkipped(refundCount == null ? 0 : refundCount.intValue());

        // 5. 生成真人语气总结
        report.setSummary(generateSummary(stallId, report));
        return report;
    }

    private String generateSummary(Long stallId, AiProcessReportVO report) {
        Stall stall = stallMapper.selectById(stallId);
        String stallName = stall == null ? "档口" : stall.getName();
        int done = report.getOrderDone();
        int cs = report.getCsReplied();
        int review = report.getReviewReplied();
        int refund = report.getRefundSkipped();
        String fallback = "已自动完成 " + done + " 笔出餐、回复 " + cs + " 条客服消息、回复 " + review + " 条评价"
                + (refund > 0 ? "；另有 " + refund + " 笔退款需人工审核" : "") + "。";
        try {
            String system = "你是校园食堂「" + stallName + "」档口的经营助理。请用老板能看懂的、亲切自然的口吻，把 AI 自动处理待办的结果总结成 1-2 句话。不要编造数据，只输出总结正文。";
            String user = "本次 AI 一键处理结果：完成出餐 " + done + " 笔，回复客服 " + cs + " 条，回复评价 " + review
                    + " 条，退款 " + refund + " 笔已跳过留待人工审核。";
            AiResult result = router.complete(ModelRole.FAST, system, user, 120, 0.8);
            String content = result.content();
            if (content != null && !content.isBlank()) {
                return AgentOrchestrator.sanitize(content);
            }
        } catch (Exception e) {
            log.warn("AI 处理总结生成失败，降级为固定文案：{}", e.getMessage());
        }
        return fallback;
    }
}
