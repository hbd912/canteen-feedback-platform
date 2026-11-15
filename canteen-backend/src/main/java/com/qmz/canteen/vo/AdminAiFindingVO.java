package com.qmz.canteen.vo;

import lombok.Data;

import java.util.List;

/** 工作台 AI 值班助理的一条问题研判。 */
@Data
public class AdminAiFindingVO {

    /** 稳定的聚类键，用于前端 key 与后续动作回写。 */
    private String id;

    private String title;
    private String category;
    private String categoryLabel;

    /** P0 立即处理 / P1 今日处理 / P2 观察。 */
    private String severity;
    private String severityLabel;

    /** 该聚类包含的原始条目数与去重学生数。 */
    private int count;
    private int users;
    private int stallCount;
    private String scopeLabel;

    private String stallName;
    private Long stallId;
    private String sourceType;
    private Long sourceId;
    /** 该聚类下的全部原始业务 ID（用于自动处理时批量回写，避免只处理代表条目） */
    private List<Long> itemIds;
    private String sample;
    private String reason;
    private String aiNote;

    /** HANDLE / SOOTHE / OBSERVE。 */
    private String suggestedAction;
    private String actionLabel;
    private String suggestedReply;
    private String targetUrl;
    private List<String> evidence;
    private double confidence;
}
