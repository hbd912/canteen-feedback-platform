package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DishAdVO {

    private Long id;
    private Long stallId;
    private String stallName;
    private Long dishId;
    private String dishName;
    private String dishImage;
    private String planName;
    private BigDecimal amount;
    private Integer days;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private String statusLabel;
    private String payStatus;
    private String rejectReason;
    private LocalDateTime createTime;
    private LocalDateTime auditedTime;
}
