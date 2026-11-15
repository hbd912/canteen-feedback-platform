package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RechargeVO {

    private Long id;
    private BigDecimal amount;
    private String payNo;
    private String channel;
    private String status;
    private String statusLabel;
    /** 为空表示无需跳转外部收银台（mock 或已直接成功） */
    private String payUrl;
    /** 本次是否已直接充值成功 */
    private Boolean paid;
    private LocalDateTime createTime;
    private LocalDateTime paidTime;
}
