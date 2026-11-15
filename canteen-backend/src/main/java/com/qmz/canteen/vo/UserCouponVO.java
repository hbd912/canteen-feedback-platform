package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserCouponVO {

    private Long id;
    private Long couponId;
    private String name;
    private String type;
    /** ORDER=整单 / ITEM=单份菜 */
    private String scope;
    private Long dishId;
    private String dishName;
    private BigDecimal value;
    private BigDecimal minAmount;
    private Long stallId;
    private String stallName;
    private LocalDateTime endTime;
    private String status;
    private LocalDateTime receiveTime;
    private Long orderId;
}
