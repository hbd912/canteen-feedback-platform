package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AccountTransactionVO {

    private Long id;
    private String type;
    private String typeLabel;
    /** 变动金额：正=收入，负=支出 */
    private BigDecimal amount;
    /** 变动后余额 */
    private BigDecimal balance;
    private String refNo;
    private String remark;
    private LocalDateTime createTime;
}
