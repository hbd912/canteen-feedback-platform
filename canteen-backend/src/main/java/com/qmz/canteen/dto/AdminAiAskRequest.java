package com.qmz.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 管理员向工作台值班助理追问。 */
@Data
public class AdminAiAskRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 300, message = "问题不能超过300字")
    private String question;
}
