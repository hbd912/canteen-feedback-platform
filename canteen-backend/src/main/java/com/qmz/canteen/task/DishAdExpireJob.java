package com.qmz.canteen.task;

import com.qmz.canteen.service.DishAdService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 广告到期清理：把超过 end_time 的投放置为 EXPIRED，避免过期广告继续置顶。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DishAdExpireJob {

    private final DishAdService adService;

    @Scheduled(initialDelay = 60_000, fixedDelay = 600_000)
    public void expireAds() {
        try {
            int count = adService.expire();
            if (count > 0) {
                log.info("广告到期清理：{} 条", count);
            }
        } catch (Exception e) {
            log.warn("广告到期清理失败：{}", e.getMessage());
        }
    }
}
