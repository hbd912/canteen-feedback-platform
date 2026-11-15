package com.qmz.canteen.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 小助手会话列表项 */
@Data
public class AssistantSessionVO {

    private Long id;

    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 会话内消息数（列表展示用） */
    private Long messageCount;
}
