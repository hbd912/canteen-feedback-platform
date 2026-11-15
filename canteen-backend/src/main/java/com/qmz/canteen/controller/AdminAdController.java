package com.qmz.canteen.controller;

import com.qmz.canteen.common.Result;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.AdStatsService;
import com.qmz.canteen.service.DishAdService;
import com.qmz.canteen.vo.AdStatsOverviewVO;
import com.qmz.canteen.vo.DishAdVO;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员广告审核：通过后才真正开始投放，驳回要留原因，全程可审计。
 */
@Validated
@RestController
@RequestMapping("/admin/ads")
@RequiredArgsConstructor
public class AdminAdController {

    private final DishAdService adService;
    private final AdStatsService adStatsService;

    @GetMapping
    public Result<List<DishAdVO>> list(@RequestParam(required = false) String status,
                                       @RequestParam(defaultValue = "100") Integer limit) {
        UserContext.requireAdmin();
        return Result.success(adService.adminList(status, limit == null ? 100 : limit));
    }

    @PostMapping("/{id}/approve")
    public Result<DishAdVO> approve(@PathVariable @Positive(message = "ID不合法") Long id) {
        UserContext.requireAdmin();
        return Result.success(adService.approve(id, UserContext.getUserId()));
    }

    @PostMapping("/{id}/reject")
    public Result<DishAdVO> reject(@PathVariable @Positive(message = "ID不合法") Long id,
                                   @RequestParam(required = false)
                                   @Size(max = 200, message = "驳回原因过长") String reason) {
        UserContext.requireAdmin();
        return Result.success(adService.reject(id, UserContext.getUserId(), reason));
    }

    /** 全部广告效果汇总 */
    @GetMapping("/stats")
    public Result<AdStatsOverviewVO> stats(@RequestParam(defaultValue = "7") Integer days) {
        UserContext.requireAdmin();
        return Result.success(adStatsService.overview(days == null ? 7 : days));
    }
}
