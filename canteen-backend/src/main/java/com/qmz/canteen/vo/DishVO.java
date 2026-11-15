package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DishVO {

    private Long id;
    private Long stallId;
    private String stallName;
    private String windowNo;
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
    /** 每日库存，null=不限量 */
    private Integer stockQty;
    /** 是否售罄 0否 1是 */
    private Integer soldOut;
    /** 是否为付费广告位（前端必须展示「广告」标识） */
    private Boolean isAd;
    /** 当前生效的广告ID，用于曝光/点击上报 */
    private Long adId;
    private Double avgRating;
    private Long reviewCount;
    private Double goodRate;
    /** 累计销量（销售记录 + 已支付订单） */
    private Long salesCount;
    private Boolean favorited;
    private LocalDateTime createTime;
}
