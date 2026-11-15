package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RefundVO {

    private Long id;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private String userName;
    private Long stallId;
    private String stallName;
    private BigDecimal amount;
    private String reason;
    /** 退款菜品摘要，如「宫保鸡丁×2、鱼香肉丝×1」 */
    private String itemSummary;
    private String status;
    private String statusLabel;
    private String refundNo;
    private String failReason;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime handledTime;
}
