package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("canteen_stall")
public class Stall {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String windowNo;

    /** 食堂区域：south-南区 north-北区 */
    private String area;

    /** 楼层：1/2，北区固定 1 */
    private Integer floor;

    private String location;

    private String category;

    private String managerName;

    private String managerPhone;

    private String coverImage;

    private String description;

    /** 学校评定的卫生分，0-100 */
    private java.math.BigDecimal hygieneScore;

    /** 健康证图片 */
    private String healthCertUrl;

    /** 营业执照图片 */
    private String licenseUrl;

    /** 资质审核状态：PENDING 待审核 / APPROVED 已通过 / REJECTED 已驳回 */
    public static final String CERT_PENDING = "PENDING";
    public static final String CERT_APPROVED = "APPROVED";
    public static final String CERT_REJECTED = "REJECTED";

    private String certStatus;

    /** 驳回原因 */
    private String certRejectReason;

    /** 最近提交资质时间 */
    private LocalDateTime certSubmitTime;

    /** 最近审核时间 */
    private LocalDateTime certReviewTime;

    /** 审核管理员ID */
    private Long certReviewedBy;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
