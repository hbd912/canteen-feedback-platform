package com.qmz.canteen.service;

import com.qmz.canteen.vo.AdminAiSessionVO;
import com.qmz.canteen.vo.AdminAiMessageVO;

import java.util.List;

/**
 * 管理员工作台 AI 助手会话持久化：服务端保存，换浏览器/清缓存不丢。
 */
public interface AdminAiChatService {

    /** 当前管理员会话列表（按最近更新倒序） */
    List<AdminAiSessionVO> listSessions();

    /** 新建会话，返回含欢迎消息的完整会话 */
    AdminAiSessionVO createSession();

    /** 删除会话（逻辑删除，消息一并隐藏） */
    void deleteSession(Long sessionId);

    /** 加载某会话的全部消息（消息为前端对象的 JSON 快照，原样还原） */
    List<AdminAiMessageVO> listMessages(Long sessionId);

    /**
     * 追加消息并刷新会话摘要。messages 为该会话的全量前端消息 JSON 数组
     * （前端是消息的唯一定制方，服务端只负责快照存储；全量同步避免并发补洞逻辑）。
     */
    void appendMessages(Long sessionId, String messagesJson);
}
