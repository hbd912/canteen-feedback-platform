package com.qmz.canteen.dto;

import lombok.Data;

/** 工作台 AI 会话消息全量同步请求体 */
@Data
public class AdminAiChatSyncRequest {

    /** 当前会话全部前端消息对象序列化后的 JSON 数组字符串 */
    private String messagesJson;
}
