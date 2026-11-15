package com.qmz.canteen.controller;

import com.qmz.canteen.common.Result;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.RefundService;
import com.qmz.canteen.vo.RefundVO;
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

@Validated
@RestController
@RequestMapping("/merchant/refunds")
@RequiredArgsConstructor
public class MerchantRefundController {

    private final RefundService refundService;

    @GetMapping
    public Result<List<RefundVO>> list(@RequestParam(required = false) String status) {
        return Result.success(refundService.merchantList(UserContext.requireMerchant(), status));
    }

    @PostMapping("/{id}/approve")
    public Result<RefundVO> approve(@PathVariable @Positive(message = "ID不合法") Long id) {
        return Result.success(refundService.approve(id, UserContext.getUserId(),
                false, UserContext.requireMerchant()));
    }

    @PostMapping("/{id}/reject")
    public Result<RefundVO> reject(@PathVariable @Positive(message = "ID不合法") Long id,
                                   @RequestParam(required = false)
                                   @Size(max = 300, message = "驳回原因过长") String remark) {
        return Result.success(refundService.reject(id, UserContext.getUserId(),
                false, UserContext.requireMerchant(), remark));
    }
}
