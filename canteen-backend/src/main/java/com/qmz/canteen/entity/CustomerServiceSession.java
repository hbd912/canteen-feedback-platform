package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服会话：一个学生对一个档口一条会话（OPEN/CLOSED）。
 */
@Data
@TableName("customer_service_session")
public class CustomerServiceSession {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_CLOSED = "CLOSED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 档口ID */
    private Long stallId;

    /** 学生ID */
    private Long userId;

    /** 关联订单ID（可为空） */
    private Long orderId;

    /** 最后一条消息摘要 */
    private String lastMessage;

    /** 最后消息时间 */
    private LocalDateTime lastMessageTime;

    /** 学生未读数 */
    private Integer studentUnread;

    /** 商家未读数 */
    private Integer merchantUnread;

    /** OPEN / CLOSED */
    private String status;

    /** 学生侧删除/隐藏（只影响学生自己的列表，不删对方记录） */
    private Integer studentDeleted;

    /** 商家侧删除/隐藏（只影响商家自己的列表） */
    private Integer merchantDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
