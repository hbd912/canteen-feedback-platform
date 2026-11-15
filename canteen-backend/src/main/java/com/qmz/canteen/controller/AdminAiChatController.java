package com.qmz.canteen.controller;

import com.qmz.canteen.common.Result;
import com.qmz.canteen.dto.AdminAiChatSyncRequest;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.AdminAiChatService;
import com.qmz.canteen.vo.AdminAiMessageVO;
import com.qmz.canteen.vo.AdminAiSessionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理员工作台 AI 助手会话：服务端持久化（换浏览器/清缓存不丢）。
 */
@RestController
@RequestMapping("/admin/workbench/ai-chat")
@RequiredArgsConstructor
public class AdminAiChatController {

    private final AdminAiChatService chatService;

    /** 会话列表（最近更新倒序） */
    @GetMapping("/sessions")
    public Result<List<AdminAiSessionVO>> sessions() {
        UserContext.requireAdmin();
        return Result.success(chatService.listSessions());
    }

    /** 新建会话 */
    @PostMapping("/sessions")
    public Result<AdminAiSessionVO> create() {
        UserContext.requireAdmin();
        return Result.success(chatService.createSession());
    }

    /** 删除会话（逻辑删除） */
    @DeleteMapping("/sessions/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        UserContext.requireAdmin();
        chatService.deleteSession(id);
        return Result.success();
    }

    /** 会话历史消息（前端对象 JSON 快照，原样还原） */
    @GetMapping("/sessions/{id}/messages")
    public Result<List<AdminAiMessageVO>> messages(@PathVariable Long id) {
        UserContext.requireAdmin();
        return Result.success(chatService.listMessages(id));
    }

    /** 全量同步消息（前端把当前会话的消息数组整体提交） */
    @PostMapping("/sessions/{id}/messages")
    public Result<Void> syncMessages(@PathVariable Long id,
                                     @RequestBody AdminAiChatSyncRequest body) {
        UserContext.requireAdmin();
        chatService.appendMessages(id, body.getMessagesJson());
        return Result.success();
    }
}
