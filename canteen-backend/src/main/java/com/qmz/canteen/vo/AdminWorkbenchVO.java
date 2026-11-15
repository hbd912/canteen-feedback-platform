package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 管理端工作台：经营指标 + 待办聚合 */
@Data
public class AdminWorkbenchVO {

    /** 今日已支付订单数 */
    private long todayOrders;

    /** 今日已支付订单金额合计 */
    private BigDecimal todayRevenue;

    /** 待管理员审核上架的菜品数。 */
    private long pendingDishes;
    private long pendingReviews;
    private long pendingFeedbacks;
    private long pendingMessages;
    /** 退款只读记录总数，不再作为待审核事项。 */
    private long refundRecords;

    private List<AdminTodoVO> todos;
}