package com.qmz.canteen.controller;

import com.qmz.canteen.common.PageResult;
import com.qmz.canteen.common.Result;
import com.qmz.canteen.agent.MerchantTodoAgent;
import com.qmz.canteen.dto.CertificateRequest;
import com.qmz.canteen.dto.MerchantMessageRequest;
import com.qmz.canteen.dto.MerchantReplyRequest;
import com.qmz.canteen.dto.MerchantTodoRequest;
import com.qmz.canteen.entity.MerchantTodo;
import com.qmz.canteen.entity.Stall;
import com.qmz.canteen.security.LoginUser;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.MerchantService;
import com.qmz.canteen.service.StallService;
import com.qmz.canteen.vo.AiProcessReportVO;
import com.qmz.canteen.vo.MerchantMessageVO;
import com.qmz.canteen.vo.MerchantTodoVO;
import com.qmz.canteen.vo.StallCustomerStatsVO;
import jakarta.validation.Valid;
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

@Validated
@RestController
@RequestMapping("/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;
    private final StallService stallService;
    private final MerchantTodoAgent todoAgent;

    // ---- 待办 ----

    @GetMapping("/todos")
    public Result<List<MerchantTodo>> todos() {
        return Result.success(merchantService.listTodos(UserContext.requireMerchant()));
    }

    /** 待办聚合：新订单 / 待退款 / 客服未读 / 待回复评价 + 整改/差评预警 */
    @GetMapping("/todos/summary")
    public Result<List<MerchantTodoVO>> todoSummary() {
        return Result.success(merchantService.listTodoSummary(UserContext.requireMerchant()));
    }

    @PutMapping("/todos/{id}/done")
    public Result<Void> doneTodo(@PathVariable @Positive(message = "待办ID不合法") Long id) {
        merchantService.doneTodo(id, UserContext.requireMerchant());
        return Result.success();
    }

    /** 商家 AI 一键代理：后台异步执行，接口立即返回，前端轮询 {@link #aiProcessStatus} */
    @PostMapping("/todos/ai-process")
    public Result<Void> aiProcess() {
        Long stallId = UserContext.requireMerchant();
        todoAgent.processAsync(stallId, UserContext.get());
        return Result.success();
    }

    /** AI 一键代理的处理状态与结果（running/报告/错误），前端 2 秒轮询 */
    @GetMapping("/todos/ai-process/status")
    public Result<MerchantTodoAgent.AiJobState> aiProcessStatus() {
        return Result.success(todoAgent.status(UserContext.requireMerchant()));
    }

    /** 商家档口顾客频次统计：新客/常客/老客分层 + 学生单量明细 */
    @GetMapping("/stats/customers")
    public Result<StallCustomerStatsVO> stallCustomerStats() {
        return Result.success(merchantService.stallCustomerStats(UserContext.requireMerchant()));
    }

    /** 管理员给某档口发整改敦促 */
    @PostMapping("/todos")
    public Result<Long> createInspectTodo(@RequestBody @Valid MerchantTodoRequest request) {
        UserContext.requireAdmin();
        return Result.success(merchantService.createInspectTodo(request));
    }

    // ---- 留言 ----

    @PostMapping("/messages")
    public Result<Void> sendMessage(@RequestBody @Valid MerchantMessageRequest request) {
        Long stallId = UserContext.requireMerchant();
        merchantService.sendMessage(UserContext.getUserId(), stallId, request.getContent());
        return Result.success();
    }

    @GetMapping("/messages")
    public Result<List<MerchantMessageVO>> myMessages() {
        return Result.success(merchantService.listMyMessages(UserContext.requireMerchant()));
    }

    /** 管理员查看全部商家留言 */
    @GetMapping("/messages/all")
    public Result<PageResult<MerchantMessageVO>> allMessages(@RequestParam(defaultValue = "1") Long current,
                                                             @RequestParam(defaultValue = "10") Long size) {
        UserContext.requireAdmin();
        return Result.success(merchantService.pageAllMessages(current, size));
    }

    /** 管理员回复商家留言 */
    @PostMapping("/messages/{id}/reply")
    public Result<Void> replyMessage(@PathVariable @Positive(message = "留言ID不合法") Long id,
                                     @RequestBody @Valid MerchantReplyRequest request) {
        UserContext.requireAdmin();
        merchantService.replyMessage(id, request.getReply());
        return Result.success();
    }

    // ---- 证照（健康证 / 营业执照） ----

    @GetMapping("/certificates")
    public Result<Stall> certificates() {
        return Result.success(merchantService.getCertificates(UserContext.requireMerchant()));
    }

    @PutMapping("/certificates")
    public Result<Void> updateCertificates(@RequestBody @Valid CertificateRequest request) {
        Long stallId = UserContext.requireMerchant();
        stallService.updateCertificates(stallId, request.getHealthCertUrl(), request.getLicenseUrl());
        return Result.success();
    }
}
