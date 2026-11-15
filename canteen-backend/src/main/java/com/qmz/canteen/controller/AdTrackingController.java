package com.qmz.canteen.controller;

import com.qmz.canteen.common.Result;
import com.qmz.canteen.dto.AdImpressionRequest;
import com.qmz.canteen.service.AdStatsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 广告曝光/点击上报。学生浏览列表即可触发，不需要登录也能记（用于未登录浏览的曝光）。
 */
@Validated
@RestController
@RequestMapping("/ads")
@RequiredArgsConstructor
public class AdTrackingController {

    private final AdStatsService adStatsService;

    @PostMapping("/impressions")
    public Result<Void> impressions(@RequestBody @Valid AdImpressionRequest request) {
        adStatsService.recordImpressions(request.getAdIds());
        return Result.success();
    }

    @PostMapping("/{adId}/click")
    public Result<Map<String, Object>> click(@PathVariable @Positive(message = "广告ID不合法") Long adId) {
        return Result.success(Map.of("counted", adStatsService.recordClick(adId)));
    }
}
