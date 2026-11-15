package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 个人中心「我的账户」统计视图（展示型余额）。
 */
@Data
public class AccountSummaryVO {

    /** 账户余额（展示型） */
    private BigDecimal balance;

    /** 总订单数 */
    private Long orderCount;

    /** 总消费（已支付订单金额合计） */
    private BigDecimal totalSpent;

    /** 已完成订单数 */
    private Long completedCount;

    /* ---------- 商家个人中心：经营数据（与销售情况/财务中心同口径） ---------- */
    /** 是否商家账号 */
    private Boolean merchant;

    /** 累计收益（已支付订单金额） */
    private BigDecimal businessIncome;

    /** 可提现余额 */
    private BigDecimal available;

    /** 本月收益 */
    private BigDecimal monthIncome;

    /** 本档口全部订单数（与销售情况一致） */
    private Long businessOrderCount;

    /** 本档口客户数 */
    private Long businessCustomerCount;
}
