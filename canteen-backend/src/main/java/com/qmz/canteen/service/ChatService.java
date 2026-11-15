package com.qmz.canteen.service;

import com.qmz.canteen.common.PageResult;
import com.qmz.canteen.vo.ChatAnswerVO;
import com.qmz.canteen.vo.ChatLogVO;
import com.qmz.canteen.vo.ChatMissVO;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface ChatService {

    /**
     * 提问并返回结构化答案。
     *
     * @param question 本轮提问
     * @param history  多轮历史，元素形如 {role:"user"/"assistant", content:"..."}，可为 null
     */
    ChatAnswerVO ask(String question, List<Map<String, String>> history);

    /** 兼容旧调用：单轮提问 */
    default ChatAnswerVO ask(String question) {
        return ask(question, null);
    }

    /**
     * 带会话持久化的提问：本轮问答写入小助手会话历史，并记录情绪/画像信号。
     *
     * @param sessionId 会话ID（可空：自动复用最近会话或新建）；返回答案带实际 sessionId
     */
    default ChatAnswerVO ask(String question, List<Map<String, String>> history, Long sessionId) {
        return ask(question, history);
    }

    /**
     * 流式提问：回答文本逐段通过 onDelta 回调，最终仍返回完整结构化答案。
     * <p>
     * 若智能体不可用，会自动降级到规则引擎，此时 onDelta 只在最后被调用一次。
     *
     * @param onDelta 增量文本回调
     */
    ChatAnswerVO askStream(String question, List<Map<String, String>> history, Consumer<String> onDelta);

    /** 带会话持久化的流式提问，语义同上 */
    default ChatAnswerVO askStream(String question, List<Map<String, String>> history, Long sessionId,
                                   Consumer<String> onDelta) {
        return askStream(question, history, onDelta);
    }

    /** 会话开场推荐问法 */
    List<String> suggestions();

    /** 用户对某条回答打分：1-有帮助 0-没帮助 */
    void feedback(Long logId, Integer helpful);

    PageResult<ChatLogVO> pageLogs(Long current, Long size);

    /** 命中率等统计，供管理员查看 */
    Map<String, Object> logStats();

    /** 未命中问题排行，供管理员补录知识库 */
    List<ChatMissVO> missTop(Long limit);

    /** 当前使用的推理引擎：agent / rule */
    String engine();
}
