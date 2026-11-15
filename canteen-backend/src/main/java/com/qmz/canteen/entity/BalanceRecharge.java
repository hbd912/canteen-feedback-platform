package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 余额充值记录 */
@Data
@TableName("balance_recharge")
public class BalanceRecharge {

    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_CLOSED = "CLOSED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private BigDecimal amount;

    private String payNo;

    private String channel;

    private String status;

    private String tradeNo;

    private LocalDateTime createTime;

    private LocalDateTime paidTime;

    @TableLogic
    private Integer deleted;
}
