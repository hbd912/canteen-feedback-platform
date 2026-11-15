package com.qmz.canteen.controller;

import com.qmz.canteen.common.PageResult;
import com.qmz.canteen.common.Result;
import com.qmz.canteen.dto.ChatRequest;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.AssistantSessionService;
import com.qmz.canteen.service.ChatService;
import com.qmz.canteen.vo.ChatAnswerVO;
import com.qmz.canteen.vo.ChatLogVO;
import com.qmz.canteen.vo.ChatMissVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final AssistantSessionService assistantSessionService;

    @PostMapping("/ask")
    public Result<ChatAnswerVO> ask(@RequestBody @Valid ChatRequest request) {
        return Result.success(chatService.ask(request.getQuestion(), request.getHistory(),
                parseSessionId(request.getSessionId())));
    }

    /** 当前推理引擎，前端可据此显示「智能助手」或「本地助手」 */
    @GetMapping("/engine")
    public Result<String> engine() {
        return Result.success(chatService.engine());
    }

    @GetMapping("/suggestions")
    public Result<List<String>> suggestions() {
        return Result.success(chatService.suggestions());
    }

    @PutMapping("/{id}/feedback")
    public Result<Void> feedback(@PathVariable @Positive(message = "记录ID不合法") Long id,
                                 @RequestParam @Min(value = 0, message = "评价取值不合法")
                                 @Max(value = 1, message = "评价取值不合法") Integer helpful) {
        chatService.feedback(id, helpful);
        return Result.success();
    }

    @GetMapping("/logs")
    public Result<PageResult<ChatLogVO>> logs(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于1") Long current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数不能小于1")
            @Max(value = 100, message = "每页条数最多100条") Long size) {
        UserContext.requireAdmin();
        return Result.success(chatService.pageLogs(current, size));
    }

    @GetMapping("/logs/stats")
    public Result<Map<String, Object>> logStats() {
        UserContext.requireAdmin();
        return Result.success(chatService.logStats());
    }

    @GetMapping("/logs/miss")
    public Result<List<ChatMissVO>> missTop(
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "条数不合法")
            @Max(value = 50, message = "最多查看50条") Long limit) {
        UserContext.requireAdmin();
        return Result.success(chatService.missTop(limit));
    }

    // ---------------- 小助手会话历史（学生端持久化） ----------------

    /** 我的会话列表（最近更新倒序） */
    @GetMapping("/sessions")
    public Result<List<com.qmz.canteen.vo.AssistantSessionVO>> sessions() {
        return Result.success(assistantSessionService.mySessions());
    }

    /** 某个会话的消息（含助手答案 JSON，可恢复卡片） */
    @GetMapping("/sessions/{id}/messages")
    public Result<List<com.qmz.canteen.vo.AssistantMessageVO>> sessionMessages(
            @PathVariable @Positive(message = "会话ID不合法") Long id) {
        return Result.success(assistantSessionService.myMessages(id));
    }

    /** 新建会话 */
    @PostMapping("/sessions")
    public Result<com.qmz.canteen.vo.AssistantSessionVO> createSession() {
        return Result.success(assistantSessionService.createSession());
    }

    /** 删除会话（连带消息） */
    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable @Positive(message = "会话ID不合法") Long id) {
        assistantSessionService.deleteSession(id);
        return Result.success();
    }

    /** 前端 sessionId 传的是字符串数字；空/非法一律按 null 处理 */
    private Long parseSessionId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
