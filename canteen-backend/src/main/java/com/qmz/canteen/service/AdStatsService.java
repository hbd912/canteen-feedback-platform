package com.qmz.canteen.service;

import com.qmz.canteen.vo.AdStatsOverviewVO;
import com.qmz.canteen.vo.AdStatsVO;

import java.util.List;

public interface AdStatsService {

    /** 批量记曝光：只统计仍在投放期的广告，单次上限 20 条 */
    void recordImpressions(List<Long> adIds);

    /** 记一次点击；返回是否真的计入 */
    boolean recordClick(Long adId);

    /** 支付成功后按订单项归因记转化 */
    void recordConversion(Long orderId);

    /** 单条广告的统计（含每日趋势） */
    AdStatsVO stats(Long adId, int days);

    /** 管理员：全部广告汇总 */
    AdStatsOverviewVO overview(int days);
}
