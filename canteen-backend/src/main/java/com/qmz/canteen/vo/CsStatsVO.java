package com.qmz.canteen.vo;

import lombok.Data;

/**
 * 管理员客服质检统计。
 */
@Data
public class CsStatsVO {

    /** 会话总数 */
    private long sessionTotal;
    /** 进行中会话数 */
    private long openTotal;
    /** 消息总数 */
    private long messageTotal;
    /** AI 回复消息数 */
    private long aiMessageTotal;
    /** 待商家处理（商家未读）会话数 */
    private long unhandledTotal;
}
