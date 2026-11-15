package com.qmz.canteen.task;

import com.qmz.canteen.service.ExperienceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 经验库补向量：人工回复等高频路径先落库，向量交给这里批量补，避免拖慢请求。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExperienceEmbeddingJob {

    private final ExperienceService experienceService;

    @Scheduled(initialDelay = 90_000, fixedDelay = 120_000)
    public void embedPending() {
        try {
            int count = experienceService.embedPending(20);
            if (count > 0) {
                log.info("经验库补向量：{} 条", count);
            }
        } catch (Exception e) {
            log.warn("经验库补向量失败：{}", e.getMessage());
        }
    }
}
