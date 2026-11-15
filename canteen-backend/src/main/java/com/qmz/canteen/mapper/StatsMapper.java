package com.qmz.canteen.mapper;

import com.qmz.canteen.vo.DishBriefVO;
import com.qmz.canteen.vo.DishRankVO;
import com.qmz.canteen.vo.DishScatterVO;
import com.qmz.canteen.vo.StallGoodRateVO;
import com.qmz.canteen.vo.StallScatterVO;
import com.qmz.canteen.vo.StallPopularityVO;
import com.qmz.canteen.vo.StallTreasureVO;
import com.qmz.canteen.vo.TrendVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface StatsMapper {

    Map<String, Object> selectOverview(@Param("goodThreshold") Integer goodThreshold);

    List<DishRankVO> selectDishRanking(@Param("limit") Long limit,
                                       @Param("goodThreshold") Integer goodThreshold,
                                       @Param("period") String period);

    /** 双龙榜左列：按点单数取热销菜品，perStall 限制单档口上榜数量，minGoodRate 卡口碑及格线 */
    List<DishRankVO> selectHotDishes(@Param("limit") Long limit,
                                     @Param("perStall") Integer perStall,
                                     @Param("minReviews") Integer minReviews,
                                     @Param("minGoodRate") Double minGoodRate,
                                     @Param("goodThreshold") Integer goodThreshold);

    /** 双龙榜右列：档口维度的点单数、评分、卫生分 */
    List<StallTreasureVO> selectTreasureStalls();

    List<StallGoodRateVO> selectStallGoodRate(@Param("goodThreshold") Integer goodThreshold);

    List<StallPopularityVO> selectStallPopularity(@Param("limit") Long limit);

    List<TrendVO> selectRatingTrend(@Param("days") Integer days);

    List<DishScatterVO> selectDishScatter(@Param("goodThreshold") Integer goodThreshold);

    List<StallScatterVO> selectStallScatter(@Param("goodThreshold") Integer goodThreshold);

    Map<String, Object> selectRatingDistribution(@Param("dishId") Long dishId);

    List<DishBriefVO> selectDishBrief(@Param("goodThreshold") Integer goodThreshold);

    /* ===================== 数据看板（管理端） ===================== */

    Map<String, Object> selectDashboardOverview(@Param("goodThreshold") Integer goodThreshold);

    List<Map<String, Object>> selectSalesTrend(@Param("days") Integer days);

    List<Map<String, Object>> selectCategorySales();

    List<Map<String, Object>> selectStallSalesRank(@Param("limit") Integer limit);

    List<Map<String, Object>> selectDishSalesRank(@Param("limit") Integer limit);

    List<Map<String, Object>> selectRatingDist();

    List<Map<String, Object>> selectStallRating(@Param("goodThreshold") Integer goodThreshold);
}
