package com.qmz.canteen.service;

import com.qmz.canteen.vo.AssistantMessageVO;
import com.qmz.canteen.vo.AssistantSessionVO;

import java.util.List;

/**
 * 食堂小助手会话持久化：对话历史存后端，刷新页面/换设备不丢。
 * <p>
 * 由 ChatServiceImpl 在每次 ask/askStream 成功后调用 record：
 * 用户消息与助手答案各存一条（助手消息带完整答案 JSON 以便恢复卡片）。
 */
public interface AssistantSessionService {

    /** 列出当前学生的会话（按最近更新倒序，最多 20 个） */
    List<AssistantSessionVO> mySessions();

    /** 读某个会话的消息（校验归属），不存在或不属于当前用户返回 null */
    List<AssistantMessageVO> myMessages(Long sessionId);

    /** 新建一个会话，返回会话信息 */
    AssistantSessionVO createSession();

    /** 删除（逻辑删）当前学生自己的会话 */
    void deleteSession(Long sessionId);

    /**
     * 记录一轮对话（内部调用）。
     *
     * @param userId     学生ID
     * @param sessionId  会话ID；null 时自动复用最近会话或新建
     * @param question   用户问题原文
     * @param answerText 助手回答文本
     * @param answerJson 助手完整答案 JSON（ChatAnswerVO 序列化），用于卡片恢复；可为 null
     * @return 实际写入的会话ID
     */
    Long record(Long userId, Long sessionId, String question, String answerText, String answerJson);
}
