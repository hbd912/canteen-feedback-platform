package com.qmz.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MerchantReviewReplyRequest {

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 500, message = "回复内容不能超过500字")
    private String reply;

    @Pattern(regexp = "MANUAL|AI", message = "回复来源不合法")
    private String source = "MANUAL";
}
