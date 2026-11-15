package com.qmz.canteen.controller;

import com.qmz.canteen.common.Result;
import com.qmz.canteen.dto.WithdrawRequest;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.MerchantFinanceService;
import com.qmz.canteen.vo.FinanceLedgerVO;
import com.qmz.canteen.vo.MerchantFinanceSummaryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商家财务：收益概览、流水、提现。收益口径与经营分析/销售情况一致（已支付订单）。
 */
@Validated
@RestController
@RequestMapping("/merchant/finance")
@RequiredArgsConstructor
public class MerchantFinanceController {

    private final MerchantFinanceService financeService;

    @GetMapping("/summary")
    public Result<MerchantFinanceSummaryVO> summary() {
        return Result.success(financeService.summary(UserContext.requireMerchant()));
    }

    @GetMapping("/ledger")
    public Result<List<FinanceLedgerVO>> ledger() {
        return Result.success(financeService.ledger(UserContext.requireMerchant()));
    }

    /** AI 经营简报（基于真实财务数据，DeepSeek/GLM 报告路由） */
    @GetMapping("/report")
    public Result<String> report() {
        return Result.success(financeService.aiReport(UserContext.requireMerchant()));
    }

    @PostMapping("/withdraw")
    public Result<MerchantFinanceSummaryVO> withdraw(@RequestBody @Valid WithdrawRequest request) {
        return Result.success(financeService.withdraw(
                UserContext.requireMerchant(), request.getAmount(), request.getRemark()));
    }
}