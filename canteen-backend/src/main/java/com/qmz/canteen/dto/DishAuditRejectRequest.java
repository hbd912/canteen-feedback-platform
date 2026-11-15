package com.qmz.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DishAuditRejectRequest {

    @NotBlank(message = "请填写驳回原因")
    @Size(max = 300, message = "驳回原因不能超过300字")
    private String reason;
}
