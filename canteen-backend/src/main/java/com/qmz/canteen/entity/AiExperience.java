package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 经验库：把「人工验证过的有效问答/处理方式」沉淀下来，下次检索注入提示词。
 */
@Data
@TableName("ai_experience")
public class AiExperience {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String scene;
    private String problem;
    private String solution;
    private String outcome;
    private Integer score;
    private String tags;
    private Long stallId;
    private String embedding;
    private String embeddingModel;
    private String source;
    private Long refId;
    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
