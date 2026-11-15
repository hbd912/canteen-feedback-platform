package com.qmz.canteen.vo;

import lombok.Data;

/**
 * 商家「AI 一键代理」处理报告：汇总本次自动处理了多少待办，
 * 以及哪些需要人工介入（如退款），并附带一段大模型生成的真人语气总结。
 */
@Data
public class AiProcessReportVO {

    /** 自动完成出餐的订单数 */
    private int orderDone;

    /** AI 自动回复的客服会话数 */
    private int csReplied;

    /** AI 自动回复的评价数 */
    private int reviewReplied;

    /** 跳过的退款数（涉及金钱，需人工审核） */
    private int refundSkipped;

    /** 大模型生成的总结（真人语气） */
    private String summary;
}
