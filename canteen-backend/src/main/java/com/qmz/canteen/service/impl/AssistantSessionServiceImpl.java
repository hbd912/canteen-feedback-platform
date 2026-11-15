package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.entity.AssistantMessage;
import com.qmz.canteen.entity.AssistantSession;
import com.qmz.canteen.mapper.AssistantMessageMapper;
import com.qmz.canteen.mapper.AssistantSessionMapper;
import com.qmz.canteen.service.AssistantSessionService;
import com.qmz.canteen.vo.AssistantMessageVO;
import com.qmz.canteen.vo.AssistantSessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小助手会话持久化实现。
 * <p>
 * 会话策略：学生不传 sessionId 时复用「最近会话」，超过 24 小时的旧会话不再续写，
 * 自动新建一个（避免几周前的历史混进当前对话）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantSessionServiceImpl implements AssistantSessionService {

    /** 最近会话超过该时长则新开一个，避免历史串味 */
    private static final long SESSION_REUSE_WINDOW_MS = 24 * 60 * 60 * 1000L;
    private static final int MAX_SESSIONS = 20;
    private static final int MAX_MESSAGES_PER_SESSION = 100;

    private final AssistantSessionMapper sessionMapper;
    private final AssistantMessageMapper messageMapper;

    @Override
    public List<AssistantSessionVO> mySessions() {
        Long userId = requireUser();
        List<AssistantSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<AssistantSession>()
                        .eq(AssistantSession::getUserId, userId)
                        .orderByDesc(AssistantSession::getUpdateTime)
                        .last("LIMIT " + MAX_SESSIONS));
        if (sessions.isEmpty()) {
            return List.of();
        }
        // 一次查出各会话消息数，避免 N+1
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (AssistantSession s : sessions) {
            counts.put(s.getId(), 0L);
        }
        List<AssistantMessage> msgs = messageMapper.selectList(
                new LambdaQueryWrapper<AssistantMessage>()
                        .in(AssistantMessage::getSessionId, counts.keySet())
                        .select(AssistantMessage::getSessionId));
        for (AssistantMessage m : msgs) {
            counts.merge(m.getSessionId(), 1L, Long::sum);
        }
        List<AssistantSessionVO> vos = new ArrayList<>();
        for (AssistantSession s : sessions) {
            AssistantSessionVO vo = new AssistantSessionVO();
            vo.setId(s.getId());
            vo.setTitle(s.getTitle());
            vo.setCreateTime(s.getCreateTime());
            vo.setUpdateTime(s.getUpdateTime());
            vo.setMessageCount(counts.getOrDefault(s.getId(), 0L));
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public List<AssistantMessageVO> myMessages(Long sessionId) {
        Long userId = requireUser();
        AssistantSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            return null;
        }
        List<AssistantMessage> msgs = messageMapper.selectList(
                new LambdaQueryWrapper<AssistantMessage>()
                        .eq(AssistantMessage::getSessionId, sessionId)
                        .orderByAsc(AssistantMessage::getId)
                        .last("LIMIT " + MAX_MESSAGES_PER_SESSION));
        List<AssistantMessageVO> vos = new ArrayList<>();
        for (AssistantMessage m : msgs) {
            AssistantMessageVO vo = new AssistantMessageVO();
            vo.setId(m.getId());
            vo.setRole(m.getRole());
            vo.setContent(m.getContent());
            vo.setAnswerJson(m.getAnswerJson());
            vo.setCreateTime(m.getCreateTime());
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public AssistantSessionVO createSession() {
        Long userId = requireUser();
        AssistantSession session = newSession(userId, "新的对话");
        AssistantSessionVO vo = new AssistantSessionVO();
        vo.setId(session.getId());
        vo.setTitle(session.getTitle());
        vo.setCreateTime(session.getCreateTime());
        vo.setUpdateTime(session.getUpdateTime());
        vo.setMessageCount(0L);
        return vo;
    }

    @Override
    public void deleteSession(Long sessionId) {
        Long userId = requireUser();
        AssistantSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException("会话不存在");
        }
        sessionMapper.deleteById(sessionId);
        messageMapper.delete(new LambdaQueryWrapper<AssistantMessage>()
                .eq(AssistantMessage::getSessionId, sessionId));
    }

    @Override
    public Long record(Long userId, Long sessionId, String question, String answerText, String answerJson) {
        if (userId == null) {
            return null;
        }
        try {
            AssistantSession session;
            if (sessionId != null) {
                session = sessionMapper.selectById(sessionId);
                if (session == null || !session.getUserId().equals(userId)) {
                    return null; // 会话不属于该用户：静默放弃持久化，不影响问答主流程
                }
            } else {
                session = latestReusable(userId);
                if (session == null) {
                    String title = question == null ? "新的对话" : truncateTitle(question);
                    session = newSession(userId, title);
                } else if (session.getTitle() == null || session.getTitle().isBlank()
                        || "新的对话".equals(session.getTitle())) {
                    AssistantSession upd = new AssistantSession();
                    upd.setId(session.getId());
                    upd.setTitle(truncateTitle(question == null ? "新的对话" : question));
                    sessionMapper.updateById(upd);
                }
            }

            LocalDateTime now = LocalDateTime.now();
            AssistantMessage userMsg = new AssistantMessage();
            userMsg.setSessionId(session.getId());
            userMsg.setRole("user");
            userMsg.setContent(question == null ? "" : question);
            userMsg.setCreateTime(now);
            messageMapper.insert(userMsg);

            AssistantMessage botMsg = new AssistantMessage();
            botMsg.setSessionId(session.getId());
            botMsg.setRole("assistant");
            botMsg.setContent(answerText == null ? "" : answerText);
            botMsg.setAnswerJson(answerJson);
            botMsg.setCreateTime(now);
            messageMapper.insert(botMsg);

            AssistantSession touch = new AssistantSession();
            touch.setId(session.getId());
            touch.setUpdateTime(now);
            sessionMapper.updateById(touch);
            return session.getId();
        } catch (Exception e) {
            // 持久化失败绝不能影响问答主流程
            log.warn("助手会话持久化失败 userId={} session={}：{}", userId, sessionId, e.getMessage());
            return null;
        }
    }

    /** 最近可复用会话：24 小时内的算热会话，否则 null（新建） */
    private AssistantSession latestReusable(Long userId) {
        AssistantSession latest = sessionMapper.selectOne(
                new LambdaQueryWrapper<AssistantSession>()
                        .eq(AssistantSession::getUserId, userId)
                        .orderByDesc(AssistantSession::getUpdateTime)
                        .last("LIMIT 1"));
        if (latest == null) {
            return null;
        }
        LocalDateTime updated = latest.getUpdateTime() == null ? latest.getCreateTime() : latest.getUpdateTime();
        if (updated == null || updated.isBefore(LocalDateTime.now().minusSeconds(SESSION_REUSE_WINDOW_MS / 1000))) {
            return null;
        }
        // 会话过长就滚动新会话，防止单会话无限膨胀
        Long count = messageMapper.selectCount(new LambdaQueryWrapper<AssistantMessage>()
                .eq(AssistantMessage::getSessionId, latest.getId()));
        if (count != null && count >= MAX_MESSAGES_PER_SESSION) {
            return null;
        }
        return latest;
    }

    private AssistantSession newSession(Long userId, String title) {
        AssistantSession session = new AssistantSession();
        session.setUserId(userId);
        session.setTitle(truncateTitle(title));
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        session.setDeleted(0);
        sessionMapper.insert(session);
        return session;
    }

    private static String truncateTitle(String text) {
        String t = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) {
            return "新的对话";
        }
        return t.length() > 30 ? t.substring(0, 30) : t;
    }

    private static Long requireUser() {
        Long userId = com.qmz.canteen.security.UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        return userId;
    }
}
