package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板：stallId 为空表示平台券，否则为档口券。
 */
@Data
@TableName("coupon")
public class Coupon {

    public static final String TYPE_AMOUNT = "AMOUNT";
    public static final String TYPE_DISCOUNT = "DISCOUNT";

    /** 适用范围：整单 / 单份菜（不与其他菜累加） */
    public static final String SCOPE_ORDER = "ORDER";
    public static final String SCOPE_ITEM = "ITEM";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stallId;
    private String name;
    private String type;
    /** ORDER=整单 / ITEM=单份菜 */
    private String scope;
    /** ITEM 券限定的菜品ID（可空） */
    private Long dishId;
    private BigDecimal value;
    private BigDecimal minAmount;
    private Integer totalQty;
    private Integer issuedQty;
    private Integer perUserLimit;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    /** 客服发券专用，不在领券中心展示 */
    private Integer privateGift;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
