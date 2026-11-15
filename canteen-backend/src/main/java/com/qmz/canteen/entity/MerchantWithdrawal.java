package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家提现记录：商家把可提现余额提走的一笔流水。
 */
@Data
@TableName("merchant_withdrawal")
public class MerchantWithdrawal {

    public static final String STATUS_DONE = "DONE";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stallId;

    private BigDecimal amount;

    private String status;

    private String remark;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}