package com.qmz.canteen.controller;

import com.qmz.canteen.common.Result;
import com.qmz.canteen.dto.CouponRequest;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.CouponService;
import com.qmz.canteen.vo.CouponVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商家端优惠券管理：只能管自己档口的券。
 */
@Validated
@RestController
@RequestMapping("/merchant/coupons")
@RequiredArgsConstructor
public class MerchantCouponController {

    private final CouponService couponService;

    @PostMapping
    public Result<CouponVO> create(@RequestBody @Valid CouponRequest request) {
        Long stallId = UserContext.requireMerchant();
        return Result.success(couponService.createCoupon(stallId, UserContext.getUserId(), request));
    }

    @GetMapping
    public Result<List<CouponVO>> list() {
        return Result.success(couponService.listMerchantCoupons(UserContext.requireMerchant()));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable @Positive(message = "优惠券ID不合法") Long id,
                                     @RequestParam @Min(value = 0) @Max(value = 1) Integer status) {
        couponService.updateStatus(id, UserContext.requireMerchant(), status);
        return Result.success();
    }
}
