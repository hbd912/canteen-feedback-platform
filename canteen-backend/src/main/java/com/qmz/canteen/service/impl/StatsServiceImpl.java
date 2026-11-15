package com.qmz.canteen.service.impl;

import com.qmz.canteen.common.PageResult;
import com.qmz.canteen.mapper.StatsMapper;
import com.qmz.canteen.service.StatsService;
import com.qmz.canteen.vo.DashboardVO;
import com.qmz.canteen.vo.DishRankVO;
import com.qmz.canteen.vo.DishScatterVO;
import com.qmz.canteen.vo.DragonBoardVO;
import com.qmz.canteen.vo.OverviewVO;
import com.qmz.canteen.vo.StallGoodRateVO;
import com.qmz.canteen.vo.StallPopularityVO;
import com.qmz.canteen.vo.StallScatterVO;
import com.qmz.canteen.vo.StallTreasureVO;
import com.qmz.canteen.vo.TrendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    /** 评价次数低于该值的菜品视为新菜品，样本不足不足以判定红黑榜 */
    private static final int NEW_DISH_REVIEW_THRESHOLD = 10;
    /** 双龙榜综合分权重：点单 30% · 评分 35% · 卫生 35% */
    private static final double SALES_WEIGHT = 0.30;
    private static final double RATING_WEIGHT = 0.35;
    private static final double HYGIENE_WEIGHT = 0.35;
    /** 爆款热菜的好评率下限，避免销量高但差评多的菜上榜 */
    private static final double HOT_MIN_GOOD_RATE = 0.6;
    private static final double VALUE_RATING = 4.0;
    private static final double AVOID_RATING = 3.2;
    private static final double QUALITY_RATING = 3.8;
    private static final double QUALITY_HYGIENE = 85.0;
    private static final double BAD_RATING = 3.5;
    private static final double BAD_HYGIENE = 80.0;

    @Value("${canteen.good-rating-threshold}")
    private Integer goodThreshold;

    private final StatsMapper statsMapper;

    @Override
    public OverviewVO overview() {
        Map<String, Object> data = statsMapper.selectOverview(goodThreshold);
        OverviewVO vo = new OverviewVO();
        vo.setStallCount(toLong(data.get("stallCount")));
        vo.setDishCount(toLong(data.get("dishCount")));
        vo.setReviewCount(toLong(data.get("reviewCount")));
        vo.setFeedbackCount(toLong(data.get("feedbackCount")));
        vo.setPendingFeedbackCount(toLong(data.get("pendingFeedbackCount")));
        vo.setStudentCount(toLong(data.get("studentCount")));
        vo.setFavoriteCount(toLong(data.get("favoriteCount")));
        vo.setAvgRating(toDouble(data.get("avgRating")));
        vo.setGoodRate(toDouble(data.get("goodRate")));
        vo.setTodayReviewCount(toLong(data.get("todayReviewCount")));
        return vo;
    }

    @Override
    public List<DishRankVO> dishRanking(Long limit, String period) {
        long size = limit == null || limit < 1 ? 10 : Math.min(limit, 50);
        String p = period == null ? "all" : period;
        return statsMapper.selectDishRanking(size, goodThreshold, p);
    }

    @Override
    public DragonBoardVO dragonBoard(Long limit) {
        long size = limit == null || limit < 1 ? 5 : Math.min(limit, 10);
        DragonBoardVO vo = new DragonBoardVO();
        vo.setMinReviews(NEW_DISH_REVIEW_THRESHOLD);
        vo.setFormula("点单 30% · 评分 35% · 卫生 35%");
        vo.setHotDishes(statsMapper.selectHotDishes(size, 2, NEW_DISH_REVIEW_THRESHOLD,
                HOT_MIN_GOOD_RATE, goodThreshold));
        vo.setTreasureStalls(rankTreasureStalls(statsMapper.selectTreasureStalls(), size));
        return vo;
    }

    /**
     * 三项指标先折算成百分制再按 3:3.5:3.5 加权；点单数取对数，避免头部档口把分差拉爆
     */
    private List<StallTreasureVO> rankTreasureStalls(List<StallTreasureVO> stalls, long size) {
        if (stalls.isEmpty()) {
            return stalls;
        }
        double minSales = stalls.stream().mapToDouble(s -> Math.log1p(salesOf(s))).min().orElse(0);
        double maxSales = stalls.stream().mapToDouble(s -> Math.log1p(salesOf(s))).max().orElse(0);
        double minRating = stalls.stream().mapToDouble(s -> ratingOf(s)).min().orElse(0);
        double maxRating = stalls.stream().mapToDouble(s -> ratingOf(s)).max().orElse(0);
        double minHygiene = stalls.stream().mapToDouble(s -> hygieneOf(s)).min().orElse(0);
        double maxHygiene = stalls.stream().mapToDouble(s -> hygieneOf(s)).max().orElse(0);

        for (StallTreasureVO s : stalls) {
            double score = SALES_WEIGHT * norm(Math.log1p(salesOf(s)), minSales, maxSales)
                    + RATING_WEIGHT * norm(ratingOf(s), minRating, maxRating)
                    + HYGIENE_WEIGHT * norm(hygieneOf(s), minHygiene, maxHygiene);
            s.setComposite(BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP).doubleValue());
        }
        return stalls.stream()
                .sorted(Comparator.comparing(StallTreasureVO::getComposite).reversed()
                        .thenComparing(Comparator.comparing(StallTreasureVO::getSalesTotal).reversed()))
                .limit(size)
                .toList();
    }

    /** 指标差值过小时直接给中位分，避免放大噪声 */
    private double norm(double value, double min, double max) {
        if (max - min < 1e-6) {
            return 50.0;
        }
        return (value - min) / (max - min) * 100.0;
    }

    private long salesOf(StallTreasureVO s) {
        return s.getSalesTotal() == null ? 0L : s.getSalesTotal();
    }

    private double ratingOf(StallTreasureVO s) {
        return s.getAvgRating() == null ? 0.0 : s.getAvgRating();
    }

    private double hygieneOf(StallTreasureVO s) {
        return s.getHygieneScore() == null ? 0.0 : s.getHygieneScore().doubleValue();
    }

    @Override
    public List<StallGoodRateVO> stallGoodRate() {
        return statsMapper.selectStallGoodRate(goodThreshold);
    }

    @Override
    public List<StallPopularityVO> stallTop(Long limit) {
        long size = limit == null || limit < 1 ? 3 : Math.min(limit, 20);
        List<StallPopularityVO> list = statsMapper.selectStallPopularity(size);
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setRank(i + 1);
        }
        return list;
    }

    @Override
    public List<TrendVO> ratingTrend(Integer days) {
        int d = days == null || days < 1 ? 7 : Math.min(days, 90);
        return statsMapper.selectRatingTrend(d);
    }

    @Override
    public List<DishScatterVO> dishScatter(Long limit) {
        List<DishScatterVO> list = statsMapper.selectDishScatter(goodThreshold);
        if (list.isEmpty()) {
            return list;
        }
        // 高性价比与避雷都是相对全站均价而言的
        double avgPrice = list.stream()
                .mapToDouble(d -> d.getPrice() == null ? 0 : d.getPrice().doubleValue())
                .average().orElse(0);

        for (DishScatterVO d : list) {
            double rating = d.getAvgRating() == null ? 0 : d.getAvgRating();
            double price = d.getPrice() == null ? 0 : d.getPrice().doubleValue();
            long count = d.getReviewCount() == null ? 0 : d.getReviewCount();

            String tag;
            String label;
            // 样本量不足时先标新菜品，避免个别差评把刚上架的菜误判成避雷
            if (count < NEW_DISH_REVIEW_THRESHOLD) {
                tag = "new";
                label = "新菜品";
            } else if (rating <= AVOID_RATING && price >= avgPrice) {
                tag = "avoid";
                label = "避雷";
            } else if (rating >= VALUE_RATING && price <= avgPrice) {
                tag = "value";
                label = "高性价比";
            } else {
                tag = "normal";
                label = "一般";
            }
            d.setTag(tag);
            d.setTagLabel(label);
        }

        // 涟漪点优先展示，普通点靠后截断，避免图上全是灰点
        list.sort(Comparator.comparing((DishScatterVO d) -> tagWeight(d.getTag()))
                .thenComparing(Comparator.comparing(DishScatterVO::getReviewCount).reversed()));

        long size = limit == null || limit < 1 ? list.size() : Math.min(limit, list.size());
        return list.subList(0, (int) size);
    }

    @Override
    public List<StallScatterVO> stallScatter() {
        List<StallScatterVO> list = statsMapper.selectStallScatter(goodThreshold);
        for (StallScatterVO s : list) {
            double rating = s.getAvgRating() == null ? 0 : s.getAvgRating();
            double hygiene = s.getHygieneScore() == null ? 0 : s.getHygieneScore().doubleValue();

            String tag;
            String label;
            if (rating >= QUALITY_RATING && hygiene >= QUALITY_HYGIENE) {
                tag = "quality";
                label = "优质档口";
            } else if (rating < BAD_RATING && hygiene < BAD_HYGIENE) {
                tag = "bad";
                label = "劣质档口";
            } else if (rating >= QUALITY_RATING) {
                tag = "normal";
                label = "评分高但卫生待提升";
            } else if (hygiene >= QUALITY_HYGIENE) {
                tag = "normal";
                label = "卫生达标但口碑一般";
            } else {
                tag = "normal";
                label = "一般";
            }
            s.setTag(tag);
            s.setTagLabel(label);
            s.setComposite(round1(rating / 5 * 60 + hygiene / 100 * 40));
        }
        list.sort(Comparator.comparing(StallScatterVO::getComposite).reversed());
        return list;
    }

    private static int tagWeight(String tag) {
        return switch (tag == null ? "" : tag) {
            case "value", "avoid" -> 0;
            case "new" -> 1;
            default -> 2;
        };
    }

    private static double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /* ===================== 数据看板（管理端） ===================== */

    @Override
    public DashboardVO dashboard(Integer days) {
        int d = days == null || days < 1 ? 30 : Math.min(days, 365);
        DashboardVO vo = new DashboardVO();

        // ---- 核心指标 ----
        Map<String, Object> ov = statsMapper.selectDashboardOverview(goodThreshold);
        DashboardVO.Overview overview = new DashboardVO.Overview();
        overview.setStallCount(toLong(ov.get("stallCount")));
        overview.setDishCount(toLong(ov.get("dishCount")));
        overview.setReviewCount(toLong(ov.get("reviewCount")));
        overview.setStudentCount(toLong(ov.get("studentCount")));
        overview.setTotalSalesQty(toLong(ov.get("totalSalesQty")));
        overview.setTotalSalesAmount(toDecimal(ov.get("totalSalesAmount")));
        overview.setLatestDayQty(toLong(ov.get("latestDayQty")));
        overview.setLatestDayAmount(toDecimal(ov.get("latestDayAmount")));
        overview.setAvgDailyQty(toLong(ov.get("avgDailyQty")));
        overview.setAvgRating(toDouble(ov.get("avgRating")));
        overview.setGoodRate(toDouble(ov.get("goodRate")));
        Object dataDate = ov.get("dataDate");
        overview.setDataDate(dataDate == null ? null : dataDate.toString());
        vo.setOverview(overview);

        // ---- 销量趋势 ----
        vo.setSalesTrend(statsMapper.selectSalesTrend(d).stream().map(m -> {
            DashboardVO.TrendPoint p = new DashboardVO.TrendPoint();
            p.setDate(str(m.get("date")));
            p.setQuantity(toLong(m.get("quantity")));
            p.setAmount(toDecimal(m.get("amount")));
            return p;
        }).toList());

        // ---- 品类销量 ----
        vo.setCategorySales(statsMapper.selectCategorySales().stream().map(m -> {
            DashboardVO.CategorySales c = new DashboardVO.CategorySales();
            c.setCategory(str(m.get("category")));
            c.setQuantity(toLong(m.get("quantity")));
            c.setAmount(toDecimal(m.get("amount")));
            return c;
        }).toList());

        // ---- 档口销量排行 ----
        vo.setStallRank(statsMapper.selectStallSalesRank(10).stream().map(m -> {
            DashboardVO.StallSales s = new DashboardVO.StallSales();
            s.setStallId(toLong(m.get("stallId")));
            s.setStallName(str(m.get("stallName")));
            s.setWindowNo(str(m.get("windowNo")));
            s.setAreaLabel(str(m.get("areaLabel")));
            s.setQuantity(toLong(m.get("quantity")));
            s.setAmount(toDecimal(m.get("amount")));
            s.setAvgRating(toDouble(m.get("avgRating")));
            return s;
        }).toList());

        // ---- 菜品销量排行（多取一些，便于按档口筛选） ----
        vo.setDishRank(statsMapper.selectDishSalesRank(20).stream().map(m -> {
            DashboardVO.DishSales d2 = new DashboardVO.DishSales();
            d2.setDishId(toLong(m.get("dishId")));
            d2.setDishName(str(m.get("dishName")));
            d2.setImage(str(m.get("image")));
            d2.setCategory(str(m.get("category")));
            d2.setStallId(toLong(m.get("stallId")));
            d2.setStallName(str(m.get("stallName")));
            d2.setPrice(toDecimal(m.get("price")));
            d2.setQuantity(toLong(m.get("quantity")));
            d2.setAmount(toDecimal(m.get("amount")));
            return d2;
        }).toList());

        // ---- 评分分布 ----
        vo.setRatingDist(statsMapper.selectRatingDist().stream().map(m -> {
            DashboardVO.RatingItem r = new DashboardVO.RatingItem();
            r.setStar(m.get("star") == null ? 0 : ((Number) m.get("star")).intValue());
            r.setCount(toLong(m.get("count")));
            return r;
        }).toList());

        // ---- 各档口口碑 ----
        vo.setStallRating(statsMapper.selectStallRating(goodThreshold).stream().map(m -> {
            DashboardVO.StallRating s = new DashboardVO.StallRating();
            s.setStallId(toLong(m.get("stallId")));
            s.setStallName(str(m.get("stallName")));
            s.setWindowNo(str(m.get("windowNo")));
            s.setAvgRating(toDouble(m.get("avgRating")));
            s.setReviewCount(toLong(m.get("reviewCount")));
            s.setGoodRate(toDouble(m.get("goodRate")));
            return s;
        }).toList());

        return vo;
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
    }

    private String str(Object value) {
        return value == null ? null : value.toString();
    }

    private Long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private Double toDouble(Object value) {
        return value == null ? 0D : ((Number) value).doubleValue();
    }
}
