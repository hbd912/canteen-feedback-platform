package com.qmz.canteen.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服消息视图。
 */
@Data
public class CustomerServiceMessageVO {

    private Long id;
    private Long sessionId;
    /** STUDENT / MERCHANT / AI */
    private String senderType;
    private Long senderId;
    private String content;
    private Long dishId;
    private String dishName;
    private String dishImage;
    private java.math.BigDecimal dishPrice;
    private Integer dishStatus;

    /* ---------- 客服发券卡片 ---------- */
    private Long couponOfferId;
    /** UNCLAIMED / CLAIMED */
    private String couponOfferStatus;
    private String couponName;
    /** AMOUNT / DISCOUNT */
    private String couponType;
    /** ORDER / ITEM */
    private String couponScope;
    private java.math.BigDecimal couponValue;
    private String couponDishName;
    private LocalDateTime couponEndTime;

    private LocalDateTime createTime;
    /** 是否当前用户本人发送 */
    private Boolean self;
}
