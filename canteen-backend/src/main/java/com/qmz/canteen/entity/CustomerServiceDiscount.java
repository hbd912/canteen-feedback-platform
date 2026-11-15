package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客服AI老顾客安抚发券流水：记录AI通过「给券：」标记给某学生发的真实档口券，
 * 用于冷却判断（同一学生同一档口 30 天内最多发 1 张）与商家统计。
 */
@Data
@TableName("customer_service_discount")
public class CustomerServiceDiscount {

    /** 分层：新客/常客/老客/商家手动 */
    public static final String TIER_NEW = "新客";
    public static final String TIER_REGULAR = "常客";
    public static final String TIER_VETERAN = "老客";
    /** 商家在会话里手动发的券，不按老客门槛 */
    public static final String TIER_MANUAL = "手动";

    /** 常客下限：完成 >= 3 单即为常客 */
    public static final int REGULAR_MIN = 3;
    /** 老客下限：完成 >= 10 单即为老客；只有老客才触发「AI 安抚发券」 */
    public static final int VETERAN_MIN = 10;

    /** 按已完成订单数返回分层名称 */
    public static String tierOf(long doneCount) {
        if (doneCount >= VETERAN_MIN) {
            return TIER_VETERAN;
        }
        if (doneCount >= REGULAR_MIN) {
            return TIER_REGULAR;
        }
        return TIER_NEW;
    }

    /** 是否达到老客标准（只有老客才能拿安抚券） */
    public static boolean isVeteran(long doneCount) {
        return doneCount >= VETERAN_MIN;
    }

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stallId;
    private Long userId;
    private Long sessionId;
    private Long couponId;
    private Long userCouponId;

    /** 分层 新客/常客/老客 */
    private String tier;

    /** 折扣值(0.8=八折) */
    private BigDecimal discountValue;

    private LocalDateTime grantTime;

    @TableLogic
    private Integer deleted;
}