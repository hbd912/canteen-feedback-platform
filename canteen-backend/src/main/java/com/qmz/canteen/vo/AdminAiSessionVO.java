package com.qmz.canteen.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 管理员工作台 AI 会话条目 */
@Data
public class AdminAiSessionVO {

    private Long id;

    /** 会话标题（取首条用户消息截断） */
    private String title;

    /** 最后一条消息摘要 */
    private String lastMessage;

    private LocalDateTime lastMessageTime;

    private LocalDateTime createTime;
}
