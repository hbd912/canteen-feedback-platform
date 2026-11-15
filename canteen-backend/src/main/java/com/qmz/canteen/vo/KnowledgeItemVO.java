package com.qmz.canteen.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeItemVO {

    private Long id;
    private String kind;
    private String question;
    private String answer;
    private String category;
    private String scene;
    private String keywords;
    private Integer sortNo;
    private Integer status;
    private String tags;
    private Integer score;
    private Long stallId;
    private String source;
    private Boolean hasEmbedding;
    private LocalDateTime createTime;
}
