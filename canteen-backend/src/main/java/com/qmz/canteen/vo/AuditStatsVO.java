package com.qmz.canteen.vo;

import lombok.Data;

@Data
public class AuditStatsVO {

    private long ai;
    private long experienceHits;
    private long ads;
    private long refunds;
    private long refundFailures;
    private long total;
}
