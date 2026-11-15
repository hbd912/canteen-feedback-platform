package com.qmz.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RefundApplyRequest {

    @NotBlank(message = "请填写退款原因")
    @Size(max = 500, message = "退款原因不能超过500字")
    private String reason;

    /** 退款的菜品项ID（dining_order_item.id）；空/null 表示整单退款 */
    private List<Long> itemIds;
}
