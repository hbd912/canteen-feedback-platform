package com.qmz.canteen.controller;

import com.qmz.canteen.common.Result;
import com.qmz.canteen.dto.RechargeRequest;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.AccountTransactionService;
import com.qmz.canteen.service.BalanceRechargeService;
import com.qmz.canteen.vo.AccountTransactionVO;
import com.qmz.canteen.vo.RechargeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/balance")
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceRechargeService rechargeService;
    private final AccountTransactionService accountTransactionService;

    /** 发起余额充值 */
    @PostMapping("/recharge")
    public Result<RechargeVO> recharge(@RequestBody @Valid RechargeRequest request) {
        return Result.success(rechargeService.recharge(UserContext.getUserId(), request.getAmount()));
    }

    /** 查询充值单状态 */
    @GetMapping("/recharge/status")
    public Result<RechargeVO> status(@RequestParam String payNo) {
        return Result.success(rechargeService.status(payNo, UserContext.getUserId()));
    }

    /** mock 通道手动确认充值成功 */
    @PostMapping("/recharge/{payNo}/mock-success")
    public Result<RechargeVO> mockSuccess(@PathVariable String payNo) {
        return Result.success(rechargeService.mockSuccess(payNo, UserContext.getUserId()));
    }

    /** 我的充值记录 */
    @GetMapping("/records")
    public Result<List<RechargeVO>> records() {
        return Result.success(rechargeService.records(UserContext.getUserId()));
    }

    /** 账户明细（充值 / 消费 / 退款流水） */
    @GetMapping("/transactions")
    public Result<List<AccountTransactionVO>> transactions(@RequestParam(required = false) Integer limit) {
        return Result.success(accountTransactionService.list(UserContext.getUserId(), limit));
    }
}
