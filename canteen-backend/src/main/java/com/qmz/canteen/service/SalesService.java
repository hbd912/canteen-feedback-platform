package com.qmz.canteen.service;

import com.qmz.canteen.vo.OrderAnalysisVO;
import com.qmz.canteen.vo.SalesAnalysisVO;
import com.qmz.canteen.vo.SalesDailyVO;
import com.qmz.canteen.vo.SalesOverviewVO;
import com.qmz.canteen.vo.SalesSummaryVO;
import com.qmz.canteen.vo.TrendDishVO;

import java.util.List;

public interface SalesService {

    SalesOverviewVO overview(Long stallId);

    /** 商家销售指标汇总（菜单总数 / 总收入 / 订单数 / 客户数） */
    SalesSummaryVO summary(Long stallId);

    List<SalesDailyVO> daily(Long stallId, Integer days);

    /** 潜力菜品（趋势项目）：近 7 天销量激增 */
    List<TrendDishVO> trend(Long stallId);

    /** 商家订单分析（订单总览 + 每日订单/收入趋势） */
    OrderAnalysisVO orderAnalysis(Long stallId, Integer days);

    /** AI 销售分析：AI 选图表和解读，数据全部来自真实订单/菜品销量 */
    SalesAnalysisVO aiAnalysis(Long stallId, Integer days);

    /** 全平台销售分析（管理员）：不限档口，含退款/净收入对比、档口排行、全平台菜品销量 TOP10 */
    SalesAnalysisVO adminAnalysis(Integer days);
}
