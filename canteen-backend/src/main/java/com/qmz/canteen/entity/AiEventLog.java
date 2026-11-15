package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 事件日志：大脑每次判断与处理结果的原料，也是「经验库」的来源。
 */
@Data
@TableName("ai_event_log")
public class AiEventLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventType;
    private String source;
    private Long sourceId;
    private Long userId;
    private Long stallId;
    private String content;
    private String decisionJson;
    private String assignTo;
    private Integer needHuman;
    private String status;
    private String result;
    private String modelName;
    private Integer tokens;
    private Integer costMs;
    private LocalDateTime createTime;
}
