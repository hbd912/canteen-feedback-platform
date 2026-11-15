package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("dish")
public class Dish {

    /** 上架审核状态：PENDING 待审核 / APPROVED 已通过 / REJECTED 已驳回 */
    public static final String AUDIT_PENDING = "PENDING";
    public static final String AUDIT_APPROVED = "APPROVED";
    public static final String AUDIT_REJECTED = "REJECTED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stallId;

    private String name;

    private BigDecimal price;

    private String category;

    private String image;

    private String description;

    private Integer featured;

    private Integer status;

    /** 上架审核状态：PENDING / APPROVED / REJECTED */
    private String auditStatus;

    /** 审核备注（驳回原因） */
    private String auditRemark;

    /** 审核管理员ID */
    private Long auditBy;

    /** 最近审核时间 */
    private LocalDateTime auditTime;

    /** 每日库存，null 表示不限量 */
    private Integer stockQty;

    /** 是否售罄 0否 1是 */
    private Integer soldOut;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
