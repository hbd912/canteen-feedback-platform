package com.qmz.canteen.task;

import com.qmz.canteen.agent.MerchantReplyGenerator;
import com.qmz.canteen.mapper.DishReviewMapper;
import com.qmz.canteen.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 评价 AI 托管：对开启了 review_ai_enabled 的档口，自动回复其待处理评价。
 * 低风险写操作，可无人值守；商家随时可手动修改。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewAutoReplyJob {

    private final DishReviewMapper dishReviewMapper;
    private final MerchantReplyGenerator replyGenerator;
    private final ReviewService reviewService;

    @Scheduled(initialDelay = 30_000, fixedDelay = 90_000)
    public void autoReplyReviews() {
        try {
            List<Map<String, Object>> targets = dishReviewMapper.selectPendingForAutoReply(20);
            if (targets == null || targets.isEmpty()) {
                return;
            }
            int ok = 0;
            int fail = 0;
            for (Map<String, Object> t : targets) {
                Long reviewId = toLong(t.get("reviewId"));
                Long stallId = toLong(t.get("stallId"));
                if (reviewId == null || stallId == null) {
                    continue;
                }
                try {
                    String reply = replyGenerator.generateReviewReply(reviewId, stallId);
                    if (reply != null && !reply.isBlank()) {
                        reviewService.replyReview(reviewId, stallId, reply, "AI");
                        ok++;
                    }
                } catch (Exception e) {
                    fail++;
                    log.warn("评价 {} 自动回复失败：{}", reviewId, e.getMessage());
                }
            }
            log.info("评价 AI 托管本轮：成功 {} 条，失败 {} 条", ok, fail);
        } catch (Exception e) {
            log.warn("评价 AI 托管扫描异常：{}", e.getMessage());
        }
    }

    private static Long toLong(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }
}
