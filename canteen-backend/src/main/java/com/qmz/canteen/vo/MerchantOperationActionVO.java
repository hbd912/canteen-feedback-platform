package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MerchantOperationActionVO {

    private Long id;
    private String actionType;
    private Long dishId;
    private String dishName;
    private String dishImage;
    private BigDecimal price;
    private Integer expectedStatus;
    private Integer targetStatus;
    private String reason;
    private String status;
    private LocalDateTime createTime;

    /** 证据快照 */
    private Long salesQty;
    private Long reviewCount;
    private Long badCount;
    private BigDecimal avgRating;
}
