package com.qmz.canteen.controller;

import com.qmz.canteen.common.Result;
import com.qmz.canteen.dto.RefundApplyRequest;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.RefundService;
import com.qmz.canteen.vo.RefundVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生退款：对已支付订单申请，查看自己的退款进度。
 */
@Validated
@RestController
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @PostMapping("/orders/{id}/refund")
    public Result<RefundVO> apply(@PathVariable @Positive(message = "订单ID不合法") Long id,
                                  @RequestBody @Valid RefundApplyRequest request) {
        return Result.success(refundService.apply(id, UserContext.getUserId(), request.getReason(), request.getItemIds()));
    }

    @GetMapping("/my/refunds")
    public Result<List<RefundVO>> my() {
        return Result.success(refundService.myRefunds(UserContext.getUserId()));
    }
}
