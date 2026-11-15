package com.qmz.canteen.controller;

import com.qmz.canteen.common.Result;
import com.qmz.canteen.dto.CreateSessionRequest;
import com.qmz.canteen.dto.CsConfigRequest;
import com.qmz.canteen.dto.GrantCouponRequest;
import com.qmz.canteen.dto.QuickReplyRequest;
import com.qmz.canteen.dto.SendMessageRequest;
import com.qmz.canteen.entity.MerchantCsConfig;
import com.qmz.canteen.entity.MerchantQuickReply;
import com.qmz.canteen.service.CustomerServiceService;
import com.qmz.canteen.vo.CustomerServiceMessageVO;
import com.qmz.canteen.vo.CustomerServiceSessionVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/customer-service")
@RequiredArgsConstructor
public class CustomerServiceController {

    private final CustomerServiceService customerServiceService;

    /** 会话列表（学生看自己的，商家看本档口的） */
    @GetMapping("/sessions")
    public Result<List<CustomerServiceSessionVO>> sessions() {
        return Result.success(customerServiceService.listSessions());
    }

    /** 学生创建/复用会话（newSession=true 时强制新建，支持多会话切换） */
    @PostMapping("/sessions")
    public Result<CustomerServiceSessionVO> create(@RequestBody @Valid CreateSessionRequest request) {
        return Result.success(customerServiceService.createOrGetSession(
                request.getStallId(), request.getOrderId(), Boolean.TRUE.equals(request.getNewSession())));
    }

    /** 拉取会话消息（本侧未读清零） */
    @GetMapping("/sessions/{id}/messages")
    public Result<List<CustomerServiceMessageVO>> messages(
            @PathVariable @Positive(message = "会话ID不合法") Long id) {
        return Result.success(customerServiceService.getMessages(id));
    }

    /** SSE 实时消息流：学生/商家订阅后，新消息立即推送 */
    @GetMapping(value = "/sessions/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable @Positive(message = "会话ID不合法") Long id) {
        return customerServiceService.subscribe(id);
    }

    /** 发送消息（学生/商家），返回刷新后的全部消息 */
    @PostMapping("/sessions/{id}/messages")
    public Result<List<CustomerServiceMessageVO>> send(
            @PathVariable @Positive(message = "会话ID不合法") Long id,
            @RequestBody @Valid SendMessageRequest request) {
        return Result.success(customerServiceService.sendMessage(id, request.getContent(), request.getDishId()));
    }

    /** 商家请求 AI 生成回复建议 */
    @PostMapping("/sessions/{id}/ai-suggest")
    public Result<Map<String, String>> aiSuggest(
            @PathVariable @Positive(message = "会话ID不合法") Long id) {
        return Result.success(Map.of("reply", customerServiceService.suggestAiReply(id)));
    }

    /** 删除/隐藏会话：学生只隐藏自己的列表，商家只隐藏本档口的列表，互不影响对方记录 */
    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable @Positive(message = "会话ID不合法") Long id) {
        customerServiceService.deleteSession(id);
        return Result.success();
    }

    /** 当前角色「全部已读」：清空学生或商家侧所有会话未读（铃铛小红点一并消失） */
    @PutMapping("/read-all")
    public Result<Void> readAll() {
        customerServiceService.readAll();
        return Result.success();
    }

    /** 单个会话已读：聊天窗口开着时来新消息，立刻清掉该会话未读 */
    @PutMapping("/sessions/{id}/read")
    public Result<Void> readSession(@PathVariable @Positive(message = "会话ID不合法") Long id) {
        customerServiceService.readSession(id);
        return Result.success();
    }

    /** 商家在会话里手动发一张可领取券（受客服设置里的规则约束） */
    @PostMapping("/sessions/{id}/grant-coupon")
    public Result<Map<String, Object>> grantCoupon(
            @PathVariable @Positive(message = "会话ID不合法") Long id,
            @RequestBody(required = false) GrantCouponRequest request) {
        return Result.success(customerServiceService.grantCouponManually(
                id,
                request == null ? null : request.getRate(),
                request == null ? null : request.getScope(),
                request == null ? null : request.getDishId()));
    }

    /** 商家读智能客服配置 */
    @GetMapping("/config")
    public Result<MerchantCsConfig> getConfig() {
        return Result.success(customerServiceService.getConfig());
    }

    /** 商家更新智能客服配置 */
    @PutMapping("/config")
    public Result<MerchantCsConfig> updateConfig(@RequestBody @Valid CsConfigRequest request) {
        return Result.success(customerServiceService.updateConfig(request));
    }

    /** 快捷回复列表 */
    @GetMapping("/quick-replies")
    public Result<List<MerchantQuickReply>> quickReplies() {
        return Result.success(customerServiceService.listQuickReplies());
    }

    @PostMapping("/quick-replies")
    public Result<MerchantQuickReply> addQuickReply(@RequestBody @Valid QuickReplyRequest request) {
        return Result.success(customerServiceService.addQuickReply(request));
    }

    @PutMapping("/quick-replies/{id}")
    public Result<MerchantQuickReply> updateQuickReply(
            @PathVariable @Positive(message = "ID不合法") Long id,
            @RequestBody @Valid QuickReplyRequest request) {
        return Result.success(customerServiceService.updateQuickReply(id, request));
    }

    @DeleteMapping("/quick-replies/{id}")
    public Result<Void> deleteQuickReply(@PathVariable @Positive(message = "ID不合法") Long id) {
        customerServiceService.deleteQuickReply(id);
        return Result.success();
    }

    /** 当前用户客服未读总数 */
    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount() {
        return Result.success(Map.of("count", customerServiceService.unreadCount()));
    }
}
