package com.qmz.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 大脑决策结果。只允许预设角色，防止模型返回未知值。
 */
@Data
public class AiDecision {

    /** CUSTOMER_SERVICE / MERCHANT / ADMIN / CODE_OPS / HUMAN */
    private String assignTo;

    private String reason;

    private Boolean needHuman;

    private String action;
}
