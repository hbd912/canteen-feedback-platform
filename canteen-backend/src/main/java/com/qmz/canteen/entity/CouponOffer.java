package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服发券 offer：商家在会话里发一张券卡片，学生点击领取后才真正到账。
 */
@Data
@TableName("coupon_offer")
public class CouponOffer {

    public static final String STATUS_UNCLAIMED = "UNCLAIMED";
    public static final String STATUS_CLAIMED = "CLAIMED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stallId;

    private Long userId;

    private Long couponId;

    private Long sessionId;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime claimTime;

    @TableLogic
    private Integer deleted;
}