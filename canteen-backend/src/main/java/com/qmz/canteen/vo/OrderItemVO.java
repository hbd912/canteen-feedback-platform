package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemVO {

    private Long id;
    private Long orderId;
    private Long dishId;
    private String dishName;
    /** 菜品图片地址（用于订单列表展示） */
    private String dishImage;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal amount;
    /** 该菜品是否已退款（部分退款时用于区分哪些退了哪些没退） */
    private Boolean refunded;
}
