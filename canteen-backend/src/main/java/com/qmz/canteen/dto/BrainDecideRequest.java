package com.qmz.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BrainDecideRequest {

    @NotBlank(message = "事件类型不能为空")
    @Size(max = 40, message = "事件类型过长")
    private String eventType;

    @Size(max = 20, message = "来源过长")
    private String source;

    private Long sourceId;

    private Long stallId;

    @NotBlank(message = "事件内容不能为空")
    @Size(max = 2000, message = "事件内容不能超过2000字")
    private String content;
}
