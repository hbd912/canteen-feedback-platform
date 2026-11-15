package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 管理员菜品审核列表项 */
@Data
public class DishAuditVO {

    private Long id;
    private Long stallId;
    private String stallName;
    private String name;
    private BigDecimal price;
    private String category;
    private String image;
    private String description;
    /** 上架状态：1 在售 / 0 下架 */
    private Integer status;
    /** 审核状态：PENDING / APPROVED / REJECTED */
    private String auditStatus;
    /** 审核备注（驳回原因） */
    private String auditRemark;
    private Long auditBy;
    private LocalDateTime auditTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
