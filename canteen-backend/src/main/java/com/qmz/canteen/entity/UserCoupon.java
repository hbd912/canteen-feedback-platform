package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户领取的优惠券。
 */
@Data
@TableName("user_coupon")
public class UserCoupon {

    public static final String STATUS_UNUSED = "UNUSED";
    public static final String STATUS_USED = "USED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long couponId;
    private Long userId;
    private String status;
    private LocalDateTime receiveTime;
    private LocalDateTime useTime;
    private Long orderId;

    @TableLogic
    private Integer deleted;
}
