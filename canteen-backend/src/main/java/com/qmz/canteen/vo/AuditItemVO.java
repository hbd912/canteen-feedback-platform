package com.qmz.canteen.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditItemVO {

    /** 形如 AI:12，方便前端做 key */
    private String id;
    private String type;
    private String typeLabel;
    private String title;
    private String detail;
    private String status;
    private String statusLabel;
    private String actor;
    private LocalDateTime time;
    private Long refId;
    /** 前端跳转目标（经验库/广告/退款） */
    private String target;
}
