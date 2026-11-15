package com.qmz.canteen.service;

import com.qmz.canteen.dto.CsConfigRequest;
import com.qmz.canteen.dto.QuickReplyRequest;
import com.qmz.canteen.entity.MerchantCsConfig;
import com.qmz.canteen.entity.MerchantQuickReply;
import com.qmz.canteen.vo.CsStatsVO;
import com.qmz.canteen.vo.CustomerServiceMessageVO;
import com.qmz.canteen.vo.CustomerServiceSessionVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface CustomerServiceService {

    /** 当前用户的会话列表（学生看自己的，商家看本档口的） */
    List<CustomerServiceSessionVO> listSessions();

    /** 学生创建或复用会话（联系客服入口）。newSession=true 强制新建，已有关闭原活跃会话归档 */
    CustomerServiceSessionVO createOrGetSession(Long stallId, Long orderId, boolean newSession);

    /** 拉取某会话消息，并把当前角色该侧未读清零 */
    List<CustomerServiceMessageVO> getMessages(Long sessionId);

    /** 订阅会话消息（SSE），用于学生/商家实时收消息 */
    SseEmitter subscribe(Long sessionId);

    /** 发送消息（学生或商家），返回刷新后的全部消息；学生发消息且开启智能代回时自动 AI 回复 */
    List<CustomerServiceMessageVO> sendMessage(Long sessionId, String content, Long dishId);

    /** 商家请求 AI 生成一条回复建议（不落库） */
    String suggestAiReply(Long sessionId);

    /**
     * 商家 AI 一键代理：对某个会话生成并发送一条 AI 回复（不沉淀为人工经验）。
     * 返回 true 表示确实产生了回复，false 表示该会话无未读消息或生成失败。
     */
    boolean sendAiReply(Long sessionId);

    /** 商家读取自己的智能客服配置 */
    MerchantCsConfig getConfig();

    /** 商家更新智能客服配置 */
    MerchantCsConfig updateConfig(CsConfigRequest request);

    List<MerchantQuickReply> listQuickReplies();

    MerchantQuickReply addQuickReply(QuickReplyRequest request);

    MerchantQuickReply updateQuickReply(Long id, QuickReplyRequest request);

    void deleteQuickReply(Long id);

    /** 当前用户的客服未读消息总数 */
    long unreadCount();

    /** 删除/隐藏一个会话：学生只隐藏自己的列表，商家只隐藏本档口的列表，不影响对方记录 */
    void deleteSession(Long sessionId);

    /** 当前角色「全部已读」：清空学生侧或商家侧所有会话未读 */
    void readAll();

    /** 单个会话已读：清空当前角色在该会话的未读（聊天窗口开着时来新消息用） */
    void readSession(Long sessionId);

    /** 商家手动给会话里的学生发一张可领取的折扣券（rate 0=免单） */
    java.util.Map<String, Object> grantCouponManually(Long sessionId, java.math.BigDecimal rate,
                                                       String scope, Long dishId);

    /** 管理员只读查看消息，不清未读、不更新会话、不触发 AI。 */
    List<CustomerServiceMessageVO> adminGetMessages(Long sessionId);

    /** 管理员：全量会话（质检） */
    List<CustomerServiceSessionVO> adminListSessions(int current, int size);

    long adminSessionTotal();

    /** 管理员：客服质检统计 */
    CsStatsVO adminStats();
}
