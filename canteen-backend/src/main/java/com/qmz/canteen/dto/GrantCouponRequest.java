package com.qmz.canteen.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商家在客服会话里发券：rate 为折扣（0.9=九折，0=免单）。
 * scope=ORDER 整单券；scope=ITEM 单份菜券（可选 dishId 限定菜品）。
 */
@Data
public class GrantCouponRequest {

    private BigDecimal rate;

    /** ORDER=整单 / ITEM=单份菜 */
    private String scope = "ORDER";

    /** ITEM 券限定菜品（可空=任意单份菜） */
    private Long dishId;
}