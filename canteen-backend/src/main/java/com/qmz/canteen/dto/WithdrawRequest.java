package com.qmz.canteen.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WithdrawRequest {

    @NotNull(message = "提现金额不能为空")
    @DecimalMin(value = "0.01", message = "提现金额必须大于 0")
    private BigDecimal amount;

    @Size(max = 200, message = "备注不能超过200字")
    private String remark;
}