package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 菜品广告投放：付费置顶，必须审核通过且在投放期内才生效。
 */
@Data
@TableName("dish_ad")
public class DishAd {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_CANCELED = "CANCELED";

    public static final String PAY_UNPAID = "UNPAID";
    public static final String PAY_PAID = "PAID";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stallId;
    private Long dishId;
    private String planName;
    private BigDecimal amount;
    private Integer days;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private String payStatus;
    private String rejectReason;
    private Long createdBy;
    private Long auditedBy;
    private LocalDateTime auditedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
