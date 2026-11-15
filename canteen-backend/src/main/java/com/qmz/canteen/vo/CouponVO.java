package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CouponVO {

    private Long id;
    private Long stallId;
    private String stallName;
    private String name;
    private String type;
    /** ORDER=整单 / ITEM=单份菜 */
    private String scope;
    private Long dishId;
    private String dishName;
    private BigDecimal value;
    private BigDecimal minAmount;
    private Integer totalQty;
    private Integer issuedQty;
    private Integer perUserLimit;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    /** 当前用户是否已领过（达到每人限领） */
    private Boolean claimed;
}
