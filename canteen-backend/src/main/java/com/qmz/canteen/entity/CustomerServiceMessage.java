package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服消息。
 */
@Data
@TableName("customer_service_message")
public class CustomerServiceMessage {

    public static final String SENDER_STUDENT = "STUDENT";
    public static final String SENDER_MERCHANT = "MERCHANT";
    public static final String SENDER_AI = "AI";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private Long sessionId;

    /** STUDENT / MERCHANT / AI */
    private String senderType;

    /** 发送者用户ID（AI 消息为 null） */
    private Long senderId;

    private String content;

    /** 学生发起咨询时附带的真实菜品，可空 */
    private Long dishId;

    /** 客服发券卡片的 offer ID（可空） */
    private Long couponOfferId;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
