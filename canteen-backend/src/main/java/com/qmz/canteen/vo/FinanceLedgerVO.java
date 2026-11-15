package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家财务流水：订单收益 / 提现，按时间倒序展示。
 */
@Data
public class FinanceLedgerVO {

    public static final String TYPE_INCOME = "INCOME";
    public static final String TYPE_WITHDRAW = "WITHDRAW";

    /** INCOME=订单收益，WITHDRAW=提现 */
    private String type;

    /** 关联ID：订单ID 或 提现ID */
    private Long refId;

    /** 标题，如「订单收入」「提现到银行卡」 */
    private String title;

    /** 副标题：订单号 / 备注 */
    private String subtitle;

    /** 金额：收益为正，提现为负 */
    private BigDecimal amount;

    private LocalDateTime time;

    private String status;
}
