package com.qmz.canteen.service;

import com.qmz.canteen.common.PageResult;
import com.qmz.canteen.dto.MerchantTodoRequest;
import com.qmz.canteen.entity.MerchantTodo;
import com.qmz.canteen.entity.Stall;
import com.qmz.canteen.vo.MerchantCommStallVO;
import com.qmz.canteen.vo.MerchantMessageVO;
import com.qmz.canteen.vo.MerchantTodoVO;
import com.qmz.canteen.vo.StallCustomerStatsVO;

import java.util.List;

public interface MerchantService {

    /** 商家待办列表（触发差评预警扫描） */
    List<MerchantTodo> listTodos(Long stallId);

    /** 商家待办聚合：新订单/待退款/客服未读/待回复评价 + 整改/差评预警 */
    List<MerchantTodoVO> listTodoSummary(Long stallId);

    void doneTodo(Long id, Long stallId);

    /** 管理员发整改敦促 */
    Long createInspectTodo(MerchantTodoRequest request);

    void sendMessage(Long userId, Long stallId, String content);

    /** 管理员向商家发送带标题的通知/反馈（OUT 消息 + 通知发送给商家） */
    void adminSendMessage(Long adminId, Long stallId, String title, String content, boolean notifyMerchant);

    List<MerchantMessageVO> listMyMessages(Long stallId);

    /** 管理员分页查看全部商家消息 */
    PageResult<MerchantMessageVO> pageAllMessages(Long current, Long size);

    /** 按档口拉取完整双向线程（IN + OUT 合并按时间升序） */
    List<MerchantMessageVO> threadByStall(Long stallId);

    void replyMessage(Long id, String reply);

    /** 商家档口证照 */
    Stall getCertificates(Long stallId);

    /** 商家档口顾客频次统计：按学生在本档口已完成订单数分层（新客/常客/老客） */
    StallCustomerStatsVO stallCustomerStats(Long stallId);

    /** 管理端商家沟通中心：全部档口列表（含权重档位、未读、最近往来） */
    List<MerchantCommStallVO> commStalls(Long adminId);
}
