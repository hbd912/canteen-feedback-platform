package com.qmz.canteen.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 小助手消息项：role=user/assistant；助手消息带 answerJson 供前端恢复卡片 */
@Data
public class AssistantMessageVO {

    private Long id;

    /** user / assistant */
    private String role;

    private String content;

    /** 完整 ChatAnswerVO JSON（仅助手消息有） */
    private String answerJson;

    private LocalDateTime createTime;
}
