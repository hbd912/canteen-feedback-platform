package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 经营托管动作：AI 生成建议，商家审批后由服务端执行。
 */
@Data
@TableName("merchant_operation_action")
public class MerchantOperationAction {

    public static final String ACTION_OFF_SHELF = "DISH_OFF_SHELF";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_EXECUTED = "EXECUTED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stallId;

    private String actionType;

    private Long dishId;

    private String dishName;

    private Integer expectedStatus;

    private Integer targetStatus;

    private String reason;

    private String evidenceJson;

    private String status;

    private String modelName;

    private String createdBy;

    private Long approvedBy;

    private LocalDateTime approvedTime;

    private LocalDateTime executedTime;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
