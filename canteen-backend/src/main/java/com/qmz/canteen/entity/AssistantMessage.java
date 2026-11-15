package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 小助手消息：会话内单条消息。助手消息把完整答案 JSON 存下来以便原样恢复卡片。 */
@Data
@TableName("assistant_message")
public class AssistantMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** user / assistant */
    private String role;

    /** 消息文本 */
    private String content;

    /** 助手消息的完整 ChatAnswerVO JSON（恢复卡片用；用户消息为 null） */
    private String answerJson;

    private LocalDateTime createTime;
}
