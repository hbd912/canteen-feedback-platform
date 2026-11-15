package com.qmz.canteen.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExperienceVO {

    private Long id;
    private String scene;
    private String problem;
    private String solution;
    private String outcome;
    private Integer score;
    private String tags;
    private Long stallId;
    private String source;
    private Boolean hasEmbedding;
    private String embeddingModel;
    private LocalDateTime createTime;
    /** 检索命中分数；列表接口为 null */
    private Double matchScore;
}
