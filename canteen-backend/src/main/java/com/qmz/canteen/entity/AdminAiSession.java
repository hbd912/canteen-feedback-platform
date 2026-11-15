package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员工作台 AI 助手会话：一个管理员多条会话。
 */
@Data
@TableName("admin_ai_session")
public class AdminAiSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 管理员用户ID */
    private Long userId;

    /** 会话标题（取首条用户消息截断） */
    private String title;

    /** 最后一条消息摘要 */
    private String lastMessage;

    /** 最后消息时间 */
    private LocalDateTime lastMessageTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
