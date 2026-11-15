package com.qmz.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 1000, message = "消息内容不能超过1000字")
    private String content;

    /** 可选：本次咨询关联的菜品ID，服务端校验必须属于会话档口 */
    private Long dishId;
}
