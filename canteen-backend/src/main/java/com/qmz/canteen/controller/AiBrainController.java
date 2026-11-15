package com.qmz.canteen.controller;

import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.Result;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.dto.AiDecision;
import com.qmz.canteen.dto.BrainDecideRequest;
import com.qmz.canteen.entity.AiEventLog;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.AiBrainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 大脑：事件路由 + 日志查看。仅商家/管理员可访问。
 */
@Validated
@RestController
@RequestMapping("/ai/brain")
@RequiredArgsConstructor
public class AiBrainController {

    private final AiBrainService brainService;

    /** 提交一个事件，让大脑判断该分配给谁 */
    @PostMapping("/decide")
    public Result<AiDecision> decide(@RequestBody @Valid BrainDecideRequest request) {
        Long stallId = resolveStall(request.getStallId());
        Long userId = UserContext.getUserId();
        AiDecision decision = brainService.decide(request.getEventType(), request.getSource(),
                request.getSourceId(), userId, stallId, request.getContent());
        return Result.success(decision);
    }

    /** 查看 AI 事件日志（商家看本档口，管理员看全部） */
    @GetMapping("/logs")
    public Result<List<AiEventLog>> logs(@RequestParam(defaultValue = "50") Integer limit) {
        Long stallId = resolveStall(null);
        return Result.success(brainService.listLogs(stallId, limit == null ? 50 : limit));
    }

    /** 商家强制绑定自己档口；管理员按请求；其他人拒绝 */
    private Long resolveStall(Long requested) {
        if (UserContext.isMerchant()) {
            return UserContext.requireMerchant();
        }
        if (UserContext.isAdmin()) {
            return requested;
        }
        throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅商家或管理员可使用 AI 大脑");
    }
}
