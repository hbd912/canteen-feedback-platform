package com.qmz.canteen.payment;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class PayRefund {

    /** 原支付单号（支付宝 out_trade_no） */
    private String payNo;
    /** 退款请求号，用于幂等 */
    private String refundNo;
    private BigDecimal amount;
    private String reason;
}
