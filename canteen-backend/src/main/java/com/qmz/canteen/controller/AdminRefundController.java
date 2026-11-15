package com.qmz.canteen.controller;

import com.qmz.canteen.common.Result;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.RefundService;
import com.qmz.canteen.vo.RefundVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员退款记录：只读留痕，不再提供审核动作。
 * 退款同意/驳回由商家端处理。
 */
@Validated
@RestController
@RequestMapping("/admin/refunds")
@RequiredArgsConstructor
public class AdminRefundController {

    private final RefundService refundService;

    @GetMapping
    public Result<List<RefundVO>> list(@RequestParam(required = false) String status) {
        UserContext.requireAdmin();
        return Result.success(refundService.adminList(status));
    }
}
