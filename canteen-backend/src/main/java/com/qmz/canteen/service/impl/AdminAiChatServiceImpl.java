package com.qmz.canteen.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.entity.AdminAiMessage;
import com.qmz.canteen.entity.AdminAiSession;
import com.qmz.canteen.mapper.AdminAiMessageMapper;
import com.qmz.canteen.mapper.AdminAiSessionMapper;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.AdminAiChatService;
import com.qmz.canteen.vo.AdminAiMessageVO;
import com.qmz.canteen.vo.AdminAiSessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员工作台 AI 会话持久化。消息内容为前端对象的 JSON 快照，
 * 服务端不解析结构（前端消息形态迭代不需要后端跟着改）；
 * 权限校验在每一步：会话只能被创建者本人读写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAiChatServiceImpl implements AdminAiChatService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_MESSAGES_PER_SYNC = 200;
    private static final int MAX_SNAPSHOT_CHARS = 60000;

    private final AdminAiSessionMapper sessionMapper;
    private final AdminAiMessageMapper messageMapper;

    @Override
    public List<AdminAiSessionVO> listSessions() {
        return sessionMapper.selectByUser(UserContext.getUserId()).stream().map(this::toVo).toList();
    }

    @Override
    public AdminAiSessionVO createSession() {
        AdminAiSession s = new AdminAiSession();
        s.setUserId(UserContext.getUserId());
        s.setTitle("新会话");
        s.setLastMessageTime(LocalDateTime.now());
        sessionMapper.insert(s);
        return toVo(s);
    }

    @Override
    public void deleteSession(Long sessionId) {
        AdminAiSession s = ownedSession(sessionId);
        sessionMapper.deleteById(s.getId());
    }

    @Override
    public List<AdminAiMessageVO> listMessages(Long sessionId) {
        ownedSession(sessionId);
        return messageMapper.listBySession(sessionId).stream().map(m -> {
            AdminAiMessageVO vo = new AdminAiMessageVO();
            vo.setId(m.getId());
            vo.setSenderType(m.getSenderType());
            vo.setContent(m.getContent());
            vo.setCreateTime(m.getCreateTime());
            return vo;
        }).toList();
    }

    @Override
    public void appendMessages(Long sessionId, String messagesJson) {
        AdminAiSession s = ownedSession(sessionId);
        if (!StringUtils.hasText(messagesJson)) {
            return;
        }
        JsonNode arr;
        try {
            arr = MAPPER.readTree(messagesJson);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "消息格式不正确");
        }
        if (!arr.isArray() || arr.isEmpty()) {
            return;
        }
        if (arr.size() > MAX_MESSAGES_PER_SYNC) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "消息数量超出限制");
        }
        // 全量同步策略：先清掉旧快照再写入最新列表，保证任何一端看到的就是当前会话真相
        messageMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminAiMessage>()
                .eq(AdminAiMessage::getSessionId, s.getId()));
        String title = s.getTitle();
        String lastMessage = null;
        LocalDateTime now = LocalDateTime.now();
        int order = 0;
        for (JsonNode node : arr) {
            if (order++ >= MAX_MESSAGES_PER_SYNC) {
                break;
            }
            String sender = node.path("role").asText("");
            String kind = node.path("kind").asText("text");
            // welcome/error/insight 等富卡片都存快照；空欢迎语存占位避免加载端空白
            String content = node.toString();
            if (content.length() > MAX_SNAPSHOT_CHARS) {
                log.info("会话 {} 第 {} 条消息快照过大({}字)截断", s.getId(), order, content.length());
                content = content.substring(0, MAX_SNAPSHOT_CHARS);
            }
            AdminAiMessage m = new AdminAiMessage();
            m.setSessionId(s.getId());
            m.setSenderType("user".equals(sender) ? AdminAiMessage.SENDER_USER : AdminAiMessage.SENDER_AI);
            m.setContent(content);
            m.setCreateTime(now.plusSeconds(order));
            messageMapper.insert(m);
            // 标题取首条用户消息；摘要取最后一条可读文本
            if ("USER".equals(m.getSenderType()) && "新会话".equals(title)) {
                String text = node.path("content").asText("");
                if (StringUtils.hasText(text)) {
                    title = truncate(text, 30);
                }
            }
            String text = node.path("content").asText("");
            if (StringUtils.hasText(text) && "text".equals(kind)) {
                lastMessage = truncate(text, 100);
            }
        }
        if (!"新会话".equals(title)) {
            s.setTitle(title);
        }
        s.setLastMessage(lastMessage);
        s.setLastMessageTime(LocalDateTime.now());
        sessionMapper.updateById(s);
    }

    private AdminAiSession ownedSession(Long sessionId) {
        if (sessionId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "会话ID不能为空");
        }
        AdminAiSession s = sessionMapper.selectById(sessionId);
        // @TableLogic 使 selectById 自动过滤 deleted=1，查不到即已删除
        if (s == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "会话不存在");
        }
        if (!s.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权访问该会话");
        }
        return s;
    }

    private AdminAiSessionVO toVo(AdminAiSession s) {
        AdminAiSessionVO vo = new AdminAiSessionVO();
        vo.setId(s.getId());
        vo.setTitle(s.getTitle());
        vo.setLastMessage(s.getLastMessage());
        vo.setLastMessageTime(s.getLastMessageTime());
        vo.setCreateTime(s.getCreateTime());
        return vo;
    }

    private static String truncate(String text, int max) {
        String t = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
