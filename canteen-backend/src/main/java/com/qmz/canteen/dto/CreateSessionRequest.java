package com.qmz.canteen.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreateSessionRequest {

    @NotNull(message = "档口ID不能为空")
    @Positive(message = "档口ID不合法")
    private Long stallId;

    /** 关联订单ID（可为空） */
    @Positive(message = "订单ID不合法")
    private Long orderId;

    /** 是否强制新建会话（true 时若无活跃会话则新建；已有关闭原活跃会话归档，实现多会话切换） */
    private Boolean newSession;
}
