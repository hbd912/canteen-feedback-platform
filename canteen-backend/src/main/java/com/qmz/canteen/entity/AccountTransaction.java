package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 账户流水（余额变动明细） */
@Data
@TableName("account_transaction")
public class AccountTransaction {

    public static final String TYPE_RECHARGE = "RECHARGE";
    public static final String TYPE_CONSUME = "CONSUME";
    public static final String TYPE_REFUND = "REFUND";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String type;

    /** 变动金额：正=收入（充值/退款），负=支出（下单消费） */
    private BigDecimal amount;

    /** 变动后余额 */
    private BigDecimal balance;

    /** 关联单号：订单号 / 充值单号 */
    private String refNo;

    private String remark;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
