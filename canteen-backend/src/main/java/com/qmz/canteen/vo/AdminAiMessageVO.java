package com.qmz.canteen.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 管理员工作台 AI 消息条目：content 为前端消息对象 JSON 快照 */
@Data
public class AdminAiMessageVO {

    private Long id;

    /** USER / AI */
    private String senderType;

    /** 前端消息对象 JSON 快照 */
    private String content;

    private LocalDateTime createTime;
}
