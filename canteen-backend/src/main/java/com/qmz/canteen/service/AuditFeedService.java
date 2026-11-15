package com.qmz.canteen.service;

import com.qmz.canteen.vo.AuditFeedVO;
import com.qmz.canteen.vo.AuditStatsVO;

public interface AuditFeedService {

    /** 统一审计流：AI 决策、经验命中、广告审核、退款 */
    AuditFeedVO feed(String type, String status, int current, int size, int days);

    AuditStatsVO stats(int days);
}
