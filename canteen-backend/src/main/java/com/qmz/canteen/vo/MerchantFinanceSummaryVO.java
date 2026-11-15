package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商家财务概览。收益口径与「经营分析 / 销售情况」完全一致：
 * 已支付订单（payStatus=PAID）的订单金额之和；退款订单会变成 REFUNDED，自动不计入。
 */
@Data
public class MerchantFinanceSummaryVO {

    /** 累计收益 */
    private BigDecimal totalIncome;

    /** 已提现 */
    private BigDecimal withdrawn;

    /** 可提现余额 = 累计收益 - 已提现 */
    private BigDecimal available;

    /** 今日收益 */
    private BigDecimal todayIncome;

    /** 本月收益 */
    private BigDecimal monthIncome;

    /** 全部订单数（与销售情况同口径） */
    private long orderCount;

    /** 已支付订单数 */
    private long paidOrderCount;

    /** 客户数（去重） */
    private long customerCount;

    /** 客单价 = 累计收益 / 已支付订单数 */
    private BigDecimal avgOrderAmount;

    /** 收益口径说明，前端可直接展示 */
    private String incomeMode = "PAID_ORDERS";
}
