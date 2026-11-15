package com.qmz.canteen.controller;

import com.qmz.canteen.common.Result;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.CouponService;
import com.qmz.canteen.vo.CouponVO;
import com.qmz.canteen.vo.UserCouponVO;
import jakarta.validation.constraints.Positive;
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
 * 学生端优惠券：可领、领取、我的券。
 */
@Validated
@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @GetMapping("/available")
    public Result<List<CouponVO>> available(@RequestParam(required = false) Long stallId) {
        return Result.success(couponService.listAvailable(stallId, UserContext.getUserId()));
    }

    @GetMapping("/my")
    public Result<List<UserCouponVO>> my() {
        return Result.success(couponService.myCoupons(UserContext.getUserId()));
    }

    @PostMapping("/{id}/claim")
    public Result<UserCouponVO> claim(@PathVariable @Positive(message = "优惠券ID不合法") Long id) {
        return Result.success(couponService.claim(id, UserContext.getUserId()));
    }

    /** 领取客服发来的券卡片 */
    @PostMapping("/offers/{id}/claim")
    public Result<UserCouponVO> claimOffer(@PathVariable @Positive(message = "offerID不合法") Long id) {
        return Result.success(couponService.claimOffer(id, UserContext.getUserId()));
    }
}
