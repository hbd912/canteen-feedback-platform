package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员工作台 AI 消息。content 存前端消息对象的 JSON 快照，
 * insight 卡片/图表等富内容可完整还原。
 */
@Data
@TableName("admin_ai_message")
public class AdminAiMessage {

    public static final String SENDER_USER = "USER";
    public static final String SENDER_AI = "AI";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private Long sessionId;

    /** USER 管理员 / AI 助理 */
    private String senderType;

    /** 前端消息对象 JSON 快照 */
    private String content;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
