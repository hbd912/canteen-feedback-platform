package com.qmz.canteen.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CouponRequest {

    @NotBlank(message = "券名不能为空")
    @Size(max = 120, message = "券名过长")
    private String name;

    @NotBlank(message = "券类型不能为空")
    @Pattern(regexp = "AMOUNT|DISCOUNT", message = "券类型不合法")
    private String type;

    /** ORDER=整单券；ITEM=单份菜券（不与其他菜累加） */
    @Pattern(regexp = "ORDER|ITEM", message = "适用范围不合法")
    private String scope = "ORDER";

    /** ITEM 券限定的菜品ID（可空=任意单份菜） */
    private Long dishId;

    @NotNull(message = "面额/折扣不能为空")
    @DecimalMin(value = "0.01", message = "面额/折扣必须大于0")
    private BigDecimal value;

    @DecimalMin(value = "0", message = "最低消费不能为负")
    private BigDecimal minAmount = BigDecimal.ZERO;

    @Min(value = 0, message = "发放总量不能为负")
    private Integer totalQty = 0;

    @Min(value = 1, message = "每人限领至少为1")
    private Integer perUserLimit = 1;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 1启用 0停用 */
    private Integer status = 1;

    /** 客服发券专用：不在领券中心展示（内部使用） */
    private Integer privateGift = 0;
}
