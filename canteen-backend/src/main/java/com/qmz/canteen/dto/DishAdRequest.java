package com.qmz.canteen.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DishAdRequest {

    @NotNull(message = "请选择要推广的菜品")
    private Long dishId;

    @Size(max = 60, message = "套餐名过长")
    private String planName;

    @NotNull(message = "请填写广告费")
    @DecimalMin(value = "0", message = "广告费不能为负")
    private BigDecimal amount;

    @NotNull(message = "请填写投放天数")
    @Min(value = 1, message = "投放天数至少 1 天")
    @Max(value = 30, message = "投放天数最多 30 天")
    private Integer days;
}
