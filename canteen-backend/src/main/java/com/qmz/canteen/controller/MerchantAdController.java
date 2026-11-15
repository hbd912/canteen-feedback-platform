package com.qmz.canteen.controller;

import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.Result;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.dto.DishAdRequest;
import com.qmz.canteen.entity.DishAd;
import com.qmz.canteen.mapper.DishAdMapper;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.AdStatsService;
import com.qmz.canteen.service.DishAdService;
import com.qmz.canteen.vo.AdStatsVO;
import com.qmz.canteen.vo.DishAdVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商家广告投放：申请 -> 模拟支付 -> 等待管理员审核 -> 投放中。
 * 排序生效只认 ACTIVE + PAID + 时间窗口，商家/模型都不能直接改排序。
 */
@Validated
@RestController
@RequestMapping("/merchant/ads")
@RequiredArgsConstructor
public class MerchantAdController {

    private final DishAdService adService;
    private final AdStatsService adStatsService;
    private final DishAdMapper adMapper;

    @PostMapping
    public Result<DishAdVO> apply(@RequestBody @Valid DishAdRequest request) {
        Long stallId = UserContext.requireMerchant();
        return Result.success(adService.apply(stallId, UserContext.getUserId(), request));
    }

    /** 演示环境的模拟支付；正式环境应替换为真实支付回调 */
    @PostMapping("/{id}/pay")
    public Result<DishAdVO> pay(@PathVariable @Positive(message = "ID不合法") Long id) {
        return Result.success(adService.pay(id, UserContext.requireMerchant()));
    }

    @GetMapping
    public Result<List<DishAdVO>> list() {
        return Result.success(adService.myAds(UserContext.requireMerchant()));
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable @Positive(message = "ID不合法") Long id) {
        adService.cancel(id, UserContext.requireMerchant());
        return Result.success();
    }

    /** 单条广告效果（曝光/点击/转化 + 每日趋势） */
    @GetMapping("/{id}/stats")
    public Result<AdStatsVO> stats(@PathVariable @Positive(message = "ID不合法") Long id,
                                   @RequestParam(defaultValue = "7") Integer days) {
        Long stallId = UserContext.requireMerchant();
        DishAd ad = adMapper.selectById(id);
        if (ad == null || !stallId.equals(ad.getStallId())) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "投放记录不存在或不属于本档口");
        }
        return Result.success(adStatsService.stats(id, days == null ? 7 : days));
    }
}
