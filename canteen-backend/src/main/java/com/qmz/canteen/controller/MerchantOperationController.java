package com.qmz.canteen.controller;

import com.qmz.canteen.common.Result;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.MerchantOperationService;
import com.qmz.canteen.vo.MerchantDiagnosisVO;
import com.qmz.canteen.vo.MerchantOperationActionVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * 商家 AI 经营托管：诊断 + 下架建议审批闭环。
 */
@Validated
@RestController
@RequestMapping("/merchant/operations")
@RequiredArgsConstructor
public class MerchantOperationController {

    private final MerchantOperationService operationService;

    /** 触发一次经营诊断（真实订单数据 + LLM 总结），生成待审批建议 */
    @PostMapping("/diagnose")
    public Result<MerchantDiagnosisVO> diagnose(
            @RequestParam(defaultValue = "30") @Min(value = 7) @Max(value = 90) Integer days) {
        return Result.success(operationService.diagnose(UserContext.requireMerchant(), days));
    }

    /** 待审批建议列表 */
    @GetMapping("/actions")
    public Result<List<MerchantOperationActionVO>> actions() {
        return Result.success(operationService.listActions(UserContext.requireMerchant()));
    }

    /** 采纳并执行（仅下架这类低风险动作） */
    @PostMapping("/actions/{id}/approve")
    public Result<MerchantOperationActionVO> approve(
            @PathVariable @Positive(message = "建议ID不合法") Long id) {
        return Result.success(operationService.approve(UserContext.requireMerchant(), id));
    }

    /** 驳回建议 */
    @PostMapping("/actions/{id}/reject")
    public Result<Void> reject(@PathVariable @Positive(message = "建议ID不合法") Long id) {
        operationService.reject(UserContext.requireMerchant(), id);
        return Result.success();
    }
}
