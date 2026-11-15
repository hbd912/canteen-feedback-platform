package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("refund_request")
public class RefundRequest {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Long userId;
    private Long stallId;
    private BigDecimal amount;
    private String reason;

    /** 退款的订单菜品项ID，逗号分隔；null 表示整单退款 */
    private String itemIds;

    /** 退款菜品摘要，如「宫保鸡丁×2、鱼香肉丝×1」 */
    private String itemSummary;

    private String status;
    private String refundNo;
    private String failReason;
    private Long handledBy;
    private LocalDateTime handledTime;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
