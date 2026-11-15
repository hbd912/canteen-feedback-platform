package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qmz.canteen.agent.AiResult;
import com.qmz.canteen.agent.ModelRole;
import com.qmz.canteen.agent.MultiModelRouter;
import com.qmz.canteen.entity.DiningOrder;
import com.qmz.canteen.entity.DiningOrderItem;
import com.qmz.canteen.entity.Dish;
import com.qmz.canteen.entity.DishSale;
import com.qmz.canteen.mapper.DiningOrderItemMapper;
import com.qmz.canteen.mapper.DishMapper;
import com.qmz.canteen.mapper.DishSaleMapper;
import com.qmz.canteen.mapper.OrderMapper;
import com.qmz.canteen.service.SalesService;
import com.qmz.canteen.vo.OrderAnalysisVO;
import com.qmz.canteen.vo.SalesAnalysisVO;
import com.qmz.canteen.vo.SalesDailyVO;
import com.qmz.canteen.vo.SalesDishVO;
import com.qmz.canteen.vo.SalesOverviewVO;
import com.qmz.canteen.vo.SalesSummaryVO;
import com.qmz.canteen.vo.TrendDishVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesServiceImpl implements SalesService {

    private final DishSaleMapper dishSaleMapper;
    private final DishMapper dishMapper;
    private final OrderMapper orderMapper;
    private final DiningOrderItemMapper orderItemMapper;
    private final com.qmz.canteen.mapper.StallMapper stallMapper;
    private final MultiModelRouter router;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");

    @Override
    public SalesOverviewVO overview(Long stallId) {
        SalesOverviewVO vo = new SalesOverviewVO();
        vo.setWeekly(dishSaleMapper.selectDishSales(stallId, 7));
        vo.setMonthly(dishSaleMapper.selectDishSales(stallId, 30));
        vo.setDaily(dishSaleMapper.selectDailyIncome(stallId, 14));
        return vo;
    }

    @Override
    public SalesSummaryVO summary(Long stallId) {
        SalesSummaryVO vo = new SalesSummaryVO();

        // 菜单总数：该档口在售菜品数
        long dishCount = dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getStallId, stallId)
                .eq(Dish::getStatus, 1));
        vo.setDishCount(dishCount);

        // 订单数与收入、客户数：该档口全部订单
        List<DiningOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<DiningOrder>()
                .eq(DiningOrder::getStallId, stallId));
        vo.setOrderCount((long) orders.size());

        BigDecimal income = orders.stream()
                .filter(o -> "PAID".equals(o.getPayStatus()) && o.getTotalAmount() != null)
                .map(DiningOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setTotalIncome(income);

        long customers = orders.stream()
                .map(DiningOrder::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        vo.setCustomerCount(customers);
        return vo;
    }

    @Override
    public List<SalesDailyVO> daily(Long stallId, Integer days) {
        return dishSaleMapper.selectDailyIncome(stallId, days);
    }

    @Override
    public List<TrendDishVO> trend(Long stallId) {
        List<TrendDishVO> list = dishSaleMapper.selectTrendDishes(stallId);
        if (list == null || list.isEmpty()) {
            return List.of();
        }

        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(6);

        // 查询近 7 天各菜品每日销量，构造迷你趋势
        List<DishSale> recent = dishSaleMapper.selectList(new LambdaQueryWrapper<DishSale>()
                .eq(DishSale::getStallId, stallId)
                .ge(DishSale::getSaleDate, from));

        Map<Long, Map<LocalDate, Integer>> byDish = new HashMap<>();
        for (DishSale s : recent) {
            byDish.computeIfAbsent(s.getDishId(), k -> new HashMap<>())
                    .merge(s.getSaleDate(), s.getQuantity(), Integer::sum);
        }

        for (TrendDishVO vo : list) {
            // 增长率
            int recentQty = vo.getRecentQty() == null ? 0 : vo.getRecentQty();
            int prevQty = vo.getPreviousQty() == null ? 0 : vo.getPreviousQty();
            if (prevQty > 0) {
                vo.setGrowthRate(BigDecimal.valueOf((recentQty - prevQty) * 100.0 / prevQty)
                        .setScale(1, RoundingMode.HALF_UP));
            } else if (recentQty > 0) {
                vo.setGrowthRate(BigDecimal.valueOf(100.0).setScale(1, RoundingMode.HALF_UP));
            } else {
                vo.setGrowthRate(BigDecimal.ZERO);
            }

            // 近 7 天每日销量（迷你趋势图）
            Map<LocalDate, Integer> m = byDish.get(vo.getDishId());
            List<Integer> trend = new ArrayList<>(7);
            for (int i = 6; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                trend.add(m == null ? 0 : m.getOrDefault(d, 0));
            }
            vo.setTrend(trend);
        }
        return list;
    }

    @Override
    public OrderAnalysisVO orderAnalysis(Long stallId, Integer days) {
        int n = days == null ? 7 : days;
        OrderAnalysisVO vo = new OrderAnalysisVO();

        // 该档口全部订单（含历史，用于状态统计）
        List<DiningOrder> all = orderMapper.selectList(new LambdaQueryWrapper<DiningOrder>()
                .eq(DiningOrder::getStallId, stallId));

        long pending = all.stream().filter(o -> "PENDING".equals(o.getStatus())).count();
        long done = all.stream().filter(o -> "DONE".equals(o.getStatus())).count();
        long canceled = all.stream().filter(o -> "CANCELED".equals(o.getStatus())).count();
        vo.setPendingCount(pending);
        vo.setDoneCount(done);
        vo.setCanceledCount(canceled);

        // 今日订单数与收入
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();
        List<DiningOrder> todayOrders = all.stream()
                .filter(o -> o.getCreateTime() != null
                        && !o.getCreateTime().isBefore(todayStart)
                        && o.getCreateTime().isBefore(todayEnd))
                .toList();
        vo.setTodayOrderCount((long) todayOrders.size());
        vo.setTodayIncome(todayOrders.stream()
                .filter(o -> "PAID".equals(o.getPayStatus()) && o.getTotalAmount() != null)
                .map(DiningOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // 近 N 天每日订单数与收入
        LocalDate from = today.minusDays(n - 1);
        Map<LocalDate, Long> orderByDay = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> incomeByDay = new LinkedHashMap<>();
        for (int i = n - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            orderByDay.put(d, 0L);
            incomeByDay.put(d, BigDecimal.ZERO);
        }
        for (DiningOrder o : all) {
            if (o.getCreateTime() == null) continue;
            LocalDate d = o.getCreateTime().toLocalDate();
            if (d.isBefore(from)) continue;
            if (orderByDay.containsKey(d)) {
                orderByDay.put(d, orderByDay.get(d) + 1);
                if ("PAID".equals(o.getPayStatus()) && o.getTotalAmount() != null) {
                    incomeByDay.put(d, incomeByDay.get(d).add(o.getTotalAmount()));
                }
            }
        }

        List<Map<String, Object>> orderTrend = new ArrayList<>();
        List<Map<String, Object>> incomeTrend = new ArrayList<>();
        for (LocalDate d : orderByDay.keySet()) {
            Map<String, Object> om = new HashMap<>();
            om.put("date", d.toString());
            om.put("count", orderByDay.get(d));
            orderTrend.add(om);

            Map<String, Object> im = new HashMap<>();
            im.put("date", d.toString());
            im.put("amount", incomeByDay.get(d));
            incomeTrend.add(im);
        }
        vo.setOrderTrend(orderTrend);
        vo.setIncomeTrend(incomeTrend);
        return vo;
    }

    @Override
    public SalesAnalysisVO aiAnalysis(Long stallId, Integer days) {
        int range = days == null ? 7 : Math.min(Math.max(days, 1), 90);
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(range - 1L);

        List<DiningOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<DiningOrder>()
                .eq(DiningOrder::getStallId, stallId)
                .ge(DiningOrder::getCreateTime, start.atStartOfDay()));

        Map<LocalDate, DayStat> byDay = new TreeMap<>();
        for (int i = 0; i < range; i++) {
            byDay.put(start.plusDays(i), new DayStat());
        }
        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        statusCounts.put("待取餐", 0);
        statusCounts.put("已完成", 0);
        statusCounts.put("已退款", 0);
        statusCounts.put("已取消", 0);
        Set<Long> customers = new HashSet<>();
        long paidCount = 0;
        BigDecimal revenue = BigDecimal.ZERO;

        for (DiningOrder o : orders) {
            if (o.getCreateTime() == null) {
                continue;
            }
            DayStat st = byDay.get(o.getCreateTime().toLocalDate());
            if (st == null) {
                continue;
            }
            st.orders++;
            if ("PAID".equals(o.getPayStatus()) && o.getTotalAmount() != null) {
                st.paid++;
                st.revenue = st.revenue.add(o.getTotalAmount());
                paidCount++;
                revenue = revenue.add(o.getTotalAmount());
                if (o.getUserId() != null) {
                    customers.add(o.getUserId());
                }
            }
            statusCounts.merge(statusLabel(o.getStatus()), 1, Integer::sum);
        }

        List<String> dayLabels = new ArrayList<>();
        List<BigDecimal> dailyRevenue = new ArrayList<>();
        List<BigDecimal> dailyOrders = new ArrayList<>();
        for (Map.Entry<LocalDate, DayStat> e : byDay.entrySet()) {
            dayLabels.add(e.getKey().format(MD));
            dailyRevenue.add(e.getValue().revenue.setScale(2, RoundingMode.HALF_UP));
            dailyOrders.add(BigDecimal.valueOf(e.getValue().orders));
        }

        // 热销菜：只统计「已支付订单」的菜品明细，和收入口径保持一致
        List<Long> paidOrderIds = orders.stream()
                .filter(o -> "PAID".equals(o.getPayStatus()) && o.getId() != null)
                .map(DiningOrder::getId)
                .toList();
        List<DiningOrderItem> paidItems = paidOrderIds.isEmpty() ? List.of()
                : orderItemMapper.selectList(new LambdaQueryWrapper<DiningOrderItem>()
                .in(DiningOrderItem::getOrderId, paidOrderIds));
        Map<String, SalesDishVO> agg = new HashMap<>();
        for (DiningOrderItem item : paidItems) {
            if (item.getQuantity() == null || item.getPrice() == null || item.getQuantity() <= 0) {
                continue;
            }
            String key = item.getDishId() != null ? "id:" + item.getDishId()
                    : "name:" + Objects.toString(item.getDishName(), "");
            SalesDishVO d = agg.computeIfAbsent(key, k -> {
                SalesDishVO v = new SalesDishVO();
                v.setDishId(item.getDishId());
                v.setDishName(item.getDishName());
                v.setQuantity(0);
                v.setAmount(BigDecimal.ZERO);
                return v;
            });
            d.setQuantity(d.getQuantity() + item.getQuantity());
            d.setAmount(d.getAmount().add(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))));
        }
        List<SalesDishVO> topDishes = agg.values().stream()
                .sorted(Comparator.comparingInt((SalesDishVO d) ->
                        d.getQuantity() == null ? 0 : d.getQuantity()).reversed())
                .limit(5).toList();
        BigDecimal avgOrder = paidCount == 0 ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(paidCount), 2, RoundingMode.HALF_UP);

        // 给 AI 的真实数据摘要（AI 只做图表选型和解读，不生成数字）
        StringBuilder dataText = new StringBuilder();
        dataText.append("统计区间：近").append(range).append("天（")
                .append(start).append(" 至 ").append(today).append("）\n");
        dataText.append("总订单 ").append(orders.size()).append(" 笔，已支付 ").append(paidCount)
                .append(" 笔，收入 ").append(money(revenue)).append(" 元，客单价 ")
                .append(money(avgOrder)).append(" 元，客户 ").append(customers.size()).append(" 位。\n");
        dataText.append("每日：");
        for (Map.Entry<LocalDate, DayStat> e : byDay.entrySet()) {
            dataText.append(e.getKey().format(MD)).append(" 订单").append(e.getValue().orders)
                    .append("/收入").append(money(e.getValue().revenue)).append("；");
        }
        dataText.append("\n热销前5：");
        for (SalesDishVO d : topDishes) {
            dataText.append(d.getDishName()).append(" ").append(d.getQuantity()).append("份/")
                    .append(money(d.getAmount())).append("元；");
        }
        dataText.append("\n订单状态：");
        for (Map.Entry<String, Integer> e : statusCounts.entrySet()) {
            dataText.append(e.getKey()).append(e.getValue()).append("；");
        }

        String system = "你是校园食堂档口的销售数据分析师。根据给出的真实数据，决定展示哪些图表并写一段简报。"
                + "只输出 JSON：{\"title\":\"标题\",\"summary\":\"80字以内的整体解读\","
                + "\"insights\":[\"2-3条具体发现\"],"
                + "\"charts\":[{\"type\":\"line|bar|pie\",\"metric\":\"dailyRevenue|dailyOrders|topDishes|orderStatus\",\"title\":\"图表标题\"}]}。"
                + "要求：charts 3-4 个，至少包含 dailyRevenue 和 topDishes；"
                + "dailyRevenue/dailyOrders 用 line 或 bar，topDishes 用 bar，orderStatus 用 pie；"
                + "只能使用给定的 metric，不要编造任何数字，不要输出 JSON 以外的文字。"
                + "注意：菜品销售额是菜品原价合计，可能因优惠与订单实收略有差异，不要把它与订单收入相加或直接比较。";

        AiResult ai = null;
        String title = "近" + range + "天销售分析";
        String summary = "";
        List<String> insights = new ArrayList<>();
        List<ChartReq> reqs = new ArrayList<>();
        try {
            ai = router.complete(ModelRole.REPORT, system, dataText.toString(), 700, 0.3);
            JsonNode root = parseJson(ai.content());
            if (root != null) {
                if (hasText(root.path("title").asText(null))) {
                    title = root.path("title").asText();
                }
                summary = root.path("summary").asText("");
                JsonNode ins = root.path("insights");
                if (ins.isArray()) {
                    for (JsonNode n : ins) {
                        if (insights.size() < 3 && hasText(n.asText(null))) {
                            insights.add(n.asText());
                        }
                    }
                }
                JsonNode charts = root.path("charts");
                if (charts.isArray()) {
                    Set<String> seen = new HashSet<>();
                    for (JsonNode n : charts) {
                        String metric = n.path("metric").asText("");
                        if (!ALLOWED_METRICS.contains(metric) || seen.contains(metric)) {
                            continue;
                        }
                        String type = normalizeChartType(metric, n.path("type").asText(""));
                        // 标题统一由后端按指标生成，避免 AI 写「销售额」但图里画的是销量这类口径误导
                        reqs.add(new ChartReq(metric, type, defaultChartTitle(metric)));
                        seen.add(metric);
                        if (reqs.size() >= 4) {
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("AI 销售分析生成失败，使用默认图表配置：{}", e.getMessage());
        }

        // 保证关键图表存在
        Set<String> metrics = new HashSet<>();
        for (ChartReq r : reqs) {
            metrics.add(r.metric);
        }
        if (!metrics.contains("dailyRevenue")) {
            reqs.add(0, new ChartReq("dailyRevenue", "line", defaultChartTitle("dailyRevenue")));
        }
        if (!metrics.contains("topDishes")) {
            reqs.add(new ChartReq("topDishes", "bar", defaultChartTitle("topDishes")));
        }
        if (reqs.size() > 4) {
            reqs = new ArrayList<>(reqs.subList(0, 4));
        }

        SalesAnalysisVO vo = new SalesAnalysisVO();
        vo.setTitle(title);
        vo.setSummary(hasText(summary) ? summary
                : "近" + range + "天共 " + orders.size() + " 笔订单，已支付 " + paidCount
                + " 笔，收入 ¥" + money(revenue) + "，客单价 ¥" + money(avgOrder) + "。");
        vo.setInsights(insights);
        vo.setCharts(buildCharts(reqs, dayLabels, dailyRevenue, dailyOrders, topDishes, statusCounts));
        vo.setTable(buildDailyTable(byDay, range, today));
        vo.setDishTable(buildDishTable(topDishes, range, today));
        vo.setModel(ai == null ? "FALLBACK" : ai.model());
        vo.setGeneratedAt(LocalDateTime.now());
        vo.setDays(range);
        return vo;
    }

    /**
     * 全平台销售分析（管理员）：不限档口，聚合所有商家订单。
     * 额外统计退款金额 → 净收入、档口收入排行、全平台菜品销量 TOP10。
     */
    @Override
    public SalesAnalysisVO adminAnalysis(Integer days) {
        int range = days == null ? 7 : Math.min(Math.max(days, 1), 90);
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(range - 1L);

        List<DiningOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<DiningOrder>()
                .ge(DiningOrder::getCreateTime, start.atStartOfDay()));

        Map<LocalDate, DayStat> byDay = new TreeMap<>();
        for (int i = 0; i < range; i++) {
            byDay.put(start.plusDays(i), new AdminDayStat());
        }
        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        statusCounts.put("待取餐", 0);
        statusCounts.put("已完成", 0);
        statusCounts.put("已退款", 0);
        statusCounts.put("已取消", 0);
        Map<Long, AdminStallStat> byStall = new HashMap<>();
        // 按月聚合（YearMonth）：长周期报告给出月度汇总表
        Map<java.time.YearMonth, AdminDayStat> byMonth = new TreeMap<>();
        Set<Long> customers = new HashSet<>();
        long paidCount = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal refundTotal = BigDecimal.ZERO;
        int refundOrders = 0;

        for (DiningOrder o : orders) {
            if (o.getCreateTime() == null) {
                continue;
            }
            AdminDayStat st = (AdminDayStat) byDay.get(o.getCreateTime().toLocalDate());
            if (st == null) {
                continue;
            }
            java.time.YearMonth ym = java.time.YearMonth.from(o.getCreateTime());
            AdminDayStat mst = byMonth.computeIfAbsent(ym, k -> new AdminDayStat());
            st.orders++;
            mst.orders++;
            if ("PAID".equals(o.getPayStatus()) && o.getTotalAmount() != null) {
                st.paid++;
                mst.paid++;
                st.revenue = st.revenue.add(o.getTotalAmount());
                mst.revenue = mst.revenue.add(o.getTotalAmount());
                paidCount++;
                revenue = revenue.add(o.getTotalAmount());
                if (o.getUserId() != null) {
                    customers.add(o.getUserId());
                }
                if (o.getStallId() != null) {
                    byStall.computeIfAbsent(o.getStallId(), k -> new AdminStallStat())
                            .revenue = byStall.get(o.getStallId()).revenue.add(o.getTotalAmount());
                    byStall.get(o.getStallId()).paidOrders++;
                }
            }
            // 退款口径：支付成功且退款成功的订单 → 金额计入退款，净收入=收入-退款
            boolean refunded = "REFUNDED".equals(o.getStatus())
                    || "REFUNDED".equals(o.getRefundStatus());
            if (refunded && o.getTotalAmount() != null) {
                st.refund = st.refund.add(o.getTotalAmount());
                mst.refund = mst.refund.add(o.getTotalAmount());
                refundTotal = refundTotal.add(o.getTotalAmount());
                refundOrders++;
                if (o.getStallId() != null) {
                    byStall.computeIfAbsent(o.getStallId(), k -> new AdminStallStat())
                            .refund = byStall.get(o.getStallId()).refund.add(o.getTotalAmount());
                }
            }
            statusCounts.merge(statusLabel(o.getStatus()), 1, Integer::sum);
        }

        List<String> dayLabels = new ArrayList<>();
        List<BigDecimal> dailyRevenue = new ArrayList<>();
        List<BigDecimal> dailyOrders = new ArrayList<>();
        List<BigDecimal> dailyNet = new ArrayList<>();
        for (Map.Entry<LocalDate, DayStat> e : byDay.entrySet()) {
            AdminDayStat s = (AdminDayStat) e.getValue();
            dayLabels.add(e.getKey().format(MD));
            dailyRevenue.add(s.revenue.setScale(2, RoundingMode.HALF_UP));
            dailyOrders.add(BigDecimal.valueOf(s.orders));
            dailyNet.add(s.revenue.subtract(s.refund).setScale(2, RoundingMode.HALF_UP));
        }

        // 全平台热销菜 TOP10：已支付订单的菜品明细
        List<Long> paidOrderIds = orders.stream()
                .filter(o -> "PAID".equals(o.getPayStatus()) && o.getId() != null)
                .map(DiningOrder::getId)
                .toList();
        List<DiningOrderItem> paidItems = paidOrderIds.isEmpty() ? List.of()
                : orderItemMapper.selectList(new LambdaQueryWrapper<DiningOrderItem>()
                .in(DiningOrderItem::getOrderId, paidOrderIds));
        Map<String, SalesDishVO> agg = new HashMap<>();
        for (DiningOrderItem item : paidItems) {
            if (item.getQuantity() == null || item.getPrice() == null || item.getQuantity() <= 0) {
                continue;
            }
            String key = item.getDishId() != null ? "id:" + item.getDishId()
                    : "name:" + Objects.toString(item.getDishName(), "");
            SalesDishVO d = agg.computeIfAbsent(key, k -> {
                SalesDishVO v = new SalesDishVO();
                v.setDishId(item.getDishId());
                v.setDishName(item.getDishName());
                v.setQuantity(0);
                v.setAmount(BigDecimal.ZERO);
                return v;
            });
            d.setQuantity(d.getQuantity() + item.getQuantity());
            d.setAmount(d.getAmount().add(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))));
        }
        List<SalesDishVO> topDishes = agg.values().stream()
                .sorted(Comparator.comparingInt((SalesDishVO d) ->
                        d.getQuantity() == null ? 0 : d.getQuantity()).reversed())
                .limit(10).toList();
        BigDecimal avgOrder = paidCount == 0 ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(paidCount), 2, RoundingMode.HALF_UP);
        BigDecimal netRevenue = revenue.subtract(refundTotal);

        // 档口排行：收入降序，补档口名
        Map<Long, String> stallNames = new HashMap<>();
        if (!byStall.isEmpty()) {
            stallMapper.selectBatchIds(byStall.keySet())
                    .forEach(s -> stallNames.put(s.getId(), s.getName()));
        }
        List<Map.Entry<Long, AdminStallStat>> stallRank = byStall.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<Long, AdminStallStat> e) -> e.getValue().revenue).reversed())
                .toList();

        // 给 AI 的真实数据摘要（AI 只做图表选型和解读，不生成数字）
        StringBuilder dataText = new StringBuilder();
        dataText.append("统计口径：全平台所有档口，近").append(range).append("天（")
                .append(start).append(" 至 ").append(today).append("）\n");
        dataText.append("总订单 ").append(orders.size()).append(" 笔，已支付 ").append(paidCount)
                .append(" 笔，总收入 ").append(money(revenue)).append(" 元，退款 ").append(refundOrders)
                .append(" 笔共 ").append(money(refundTotal)).append(" 元，净收入 ")
                .append(money(netRevenue)).append(" 元，客单价 ").append(money(avgOrder))
                .append(" 元，消费客户 ").append(customers.size()).append(" 位。\n");
        dataText.append("每日（订单/收入/净收入）：");
        for (Map.Entry<LocalDate, DayStat> e : byDay.entrySet()) {
            AdminDayStat s = (AdminDayStat) e.getValue();
            dataText.append(e.getKey().format(MD)).append(" ").append(s.orders).append("/")
                    .append(money(s.revenue)).append("/").append(money(s.revenue.subtract(s.refund))).append("；");
        }
        dataText.append("\n热销前10：");
        for (SalesDishVO d : topDishes) {
            dataText.append(d.getDishName()).append(" ").append(d.getQuantity()).append("份；");
        }
        dataText.append("\n档口收入前8：");
        for (Map.Entry<Long, AdminStallStat> e : stallRank.subList(0, Math.min(8, stallRank.size()))) {
            dataText.append(stallNames.getOrDefault(e.getKey(), "档口#" + e.getKey())).append(" ")
                    .append(money(e.getValue().revenue)).append("元；");
        }
        // 长周期补月度趋势，让 AI 解读月环比变化
        if (range >= 28) {
            dataText.append("\n按月（订单/收入/退款/净收入）：");
            for (Map.Entry<java.time.YearMonth, AdminDayStat> e : byMonth.entrySet()) {
                AdminDayStat s = e.getValue();
                dataText.append(e.getKey()).append(" ").append(s.orders).append("/")
                        .append(money(s.revenue)).append("/").append(money(s.refund)).append("/")
                        .append(money(s.revenue.subtract(s.refund))).append("；");
            }
        }
        dataText.append("\n订单状态：");
        for (Map.Entry<String, Integer> e : statusCounts.entrySet()) {
            dataText.append(e.getKey()).append(e.getValue()).append("；");
        }

        String system = "你是校园食堂管理方的经营数据分析师，分析对象是全平台所有档口的销售数据。"
                + "根据给出的真实数据，决定展示哪些图表并写一段简报。"
                + "只输出 JSON：{\"title\":\"标题\",\"summary\":\"80字以内的整体解读\","
                + "\"insights\":[\"2-3条具体发现\"],"
                + "\"charts\":[{\"type\":\"line|bar|pie\",\"metric\":\"dailyRevenue|dailyOrders|topDishes|orderStatus\",\"title\":\"图表标题\"}]}。"
                + "要求：charts 3-4 个，至少包含 dailyRevenue 和 topDishes；"
                + "dailyRevenue/dailyOrders 用 line 或 bar，topDishes 用 bar，orderStatus 用 pie；"
                + "只能使用给定的 metric，不要编造任何数字，不要输出 JSON 以外的文字。"
                + "解读时关注全平台整体经营状况：收入趋势、退款率、热销品类、档口间差异。"
                + "注意：菜品销量合计是份数口径，订单金额含优惠，两者不要混算。";

        AiResult ai = null;
        String title = "全平台近" + range + "天销售分析";
        String summary = "";
        List<String> insights = new ArrayList<>();
        List<ChartReq> reqs = new ArrayList<>();
        try {
            ai = router.complete(ModelRole.REPORT, system, dataText.toString(), 700, 0.3);
            JsonNode root = parseJson(ai.content());
            if (root != null) {
                if (hasText(root.path("title").asText(null))) {
                    title = root.path("title").asText();
                }
                summary = root.path("summary").asText("");
                JsonNode ins = root.path("insights");
                if (ins.isArray()) {
                    for (JsonNode n : ins) {
                        if (insights.size() < 3 && hasText(n.asText(null))) {
                            insights.add(n.asText());
                        }
                    }
                }
                JsonNode charts = root.path("charts");
                if (charts.isArray()) {
                    Set<String> seen = new HashSet<>();
                    for (JsonNode n : charts) {
                        String metric = n.path("metric").asText("");
                        if (!ALLOWED_METRICS.contains(metric) || seen.contains(metric)) {
                            continue;
                        }
                        String type = normalizeChartType(metric, n.path("type").asText(""));
                        reqs.add(new ChartReq(metric, type, defaultChartTitle(metric)));
                        seen.add(metric);
                        if (reqs.size() >= 4) {
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("AI 全平台销售分析生成失败，使用默认图表配置：{}", e.getMessage());
        }

        Set<String> metrics = new HashSet<>();
        for (ChartReq r : reqs) {
            metrics.add(r.metric);
        }
        if (!metrics.contains("dailyRevenue")) {
            reqs.add(0, new ChartReq("dailyRevenue", "line", defaultChartTitle("dailyRevenue")));
        }
        if (!metrics.contains("topDishes")) {
            reqs.add(new ChartReq("topDishes", "bar", defaultChartTitle("topDishes")));
        }
        if (reqs.size() > 4) {
            reqs = new ArrayList<>(reqs.subList(0, 4));
        }

        SalesAnalysisVO vo = new SalesAnalysisVO();
        vo.setTitle(title);
        vo.setSummary(hasText(summary) ? summary
                : "全平台近" + range + "天共 " + orders.size() + " 笔订单，已支付 " + paidCount
                + " 笔，总收入 ¥" + money(revenue) + "，退款 ¥" + money(refundTotal)
                + "，净收入 ¥" + money(netRevenue) + "，客单价 ¥" + money(avgOrder) + "。");
        vo.setInsights(insights);
        vo.setCharts(buildCharts(reqs, dayLabels, dailyRevenue, dailyOrders, topDishes, statusCounts));
        vo.setTable(buildAdminDailyTable(byDay, range, today));
        vo.setDishTable(buildAdminDishTable(topDishes, range, today));
        vo.setStallTable(buildStallTable(stallRank, stallNames, range, today));
        // 长周期（≥28 天，即一个月/季度档）补一张按月汇总表，季度看整体趋势更直观
        if (range >= 28) {
            vo.setMonthTable(buildMonthTable(byMonth, range, today));
        }
        vo.setModel(ai == null ? "FALLBACK" : ai.model());
        vo.setGeneratedAt(LocalDateTime.now());
        vo.setDays(range);
        return vo;
    }

    private SalesAnalysisVO.Table buildAdminDailyTable(Map<LocalDate, DayStat> byDay, int range, LocalDate today) {
        SalesAnalysisVO.Table t = new SalesAnalysisVO.Table();
        t.setColumns(List.of("日期", "订单数", "已支付", "收入(元)", "退款(元)", "净收入(元)", "客单价(元)"));
        List<List<String>> rows = new ArrayList<>();
        int sumOrders = 0;
        int sumPaid = 0;
        BigDecimal sumRevenue = BigDecimal.ZERO;
        BigDecimal sumRefund = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, DayStat> e : byDay.entrySet()) {
            AdminDayStat s = (AdminDayStat) e.getValue();
            BigDecimal avg = s.paid == 0 ? BigDecimal.ZERO
                    : s.revenue.divide(BigDecimal.valueOf(s.paid), 2, RoundingMode.HALF_UP);
            rows.add(List.of(e.getKey().toString(), String.valueOf(s.orders), String.valueOf(s.paid),
                    money(s.revenue), money(s.refund), money(s.revenue.subtract(s.refund)), money(avg)));
            sumOrders += s.orders;
            sumPaid += s.paid;
            sumRevenue = sumRevenue.add(s.revenue);
            sumRefund = sumRefund.add(s.refund);
        }
        // 合计行：区间整体一眼可读
        BigDecimal totalAvg = sumPaid == 0 ? BigDecimal.ZERO
                : sumRevenue.divide(BigDecimal.valueOf(sumPaid), 2, RoundingMode.HALF_UP);
        rows.add(List.of("合计", String.valueOf(sumOrders), String.valueOf(sumPaid),
                money(sumRevenue), money(sumRefund), money(sumRevenue.subtract(sumRefund)), money(totalAvg)));
        t.setRows(rows);
        t.setFilename("全平台销售明细_近" + range + "天_" + today.toString().replace("-", "") + ".csv");
        return t;
    }

    /** 按月汇总表：月份 | 订单数 | 已支付 | 收入 | 退款 | 净收入 | 客单价，末行为合计 */
    private SalesAnalysisVO.Table buildMonthTable(Map<java.time.YearMonth, AdminDayStat> byMonth,
                                                  int range, LocalDate today) {
        SalesAnalysisVO.Table t = new SalesAnalysisVO.Table();
        t.setColumns(List.of("月份", "订单数", "已支付", "收入(元)", "退款(元)", "净收入(元)", "客单价(元)"));
        List<List<String>> rows = new ArrayList<>();
        int sumOrders = 0;
        int sumPaid = 0;
        BigDecimal sumRevenue = BigDecimal.ZERO;
        BigDecimal sumRefund = BigDecimal.ZERO;
        for (Map.Entry<java.time.YearMonth, AdminDayStat> e : byMonth.entrySet()) {
            AdminDayStat s = e.getValue();
            BigDecimal avg = s.paid == 0 ? BigDecimal.ZERO
                    : s.revenue.divide(BigDecimal.valueOf(s.paid), 2, RoundingMode.HALF_UP);
            rows.add(List.of(e.getKey().toString(), String.valueOf(s.orders), String.valueOf(s.paid),
                    money(s.revenue), money(s.refund), money(s.revenue.subtract(s.refund)), money(avg)));
            sumOrders += s.orders;
            sumPaid += s.paid;
            sumRevenue = sumRevenue.add(s.revenue);
            sumRefund = sumRefund.add(s.refund);
        }
        BigDecimal totalAvg = sumPaid == 0 ? BigDecimal.ZERO
                : sumRevenue.divide(BigDecimal.valueOf(sumPaid), 2, RoundingMode.HALF_UP);
        rows.add(List.of("合计", String.valueOf(sumOrders), String.valueOf(sumPaid),
                money(sumRevenue), money(sumRefund), money(sumRevenue.subtract(sumRefund)), money(totalAvg)));
        t.setRows(rows);
        t.setFilename("全平台按月汇总_近" + range + "天_" + today.toString().replace("-", "") + ".csv");
        return t;
    }

    private SalesAnalysisVO.Table buildAdminDishTable(List<SalesDishVO> topDishes, int range, LocalDate today) {
        SalesAnalysisVO.Table t = new SalesAnalysisVO.Table();
        t.setColumns(List.of("排名", "菜品", "销量(份)", "销售额(元)"));
        List<List<String>> rows = new ArrayList<>();
        int rank = 1;
        for (SalesDishVO d : topDishes) {
            rows.add(List.of(String.valueOf(rank++), d.getDishName(),
                    String.valueOf(d.getQuantity() == null ? 0 : d.getQuantity()), money(d.getAmount())));
        }
        t.setRows(rows);
        t.setFilename("全平台菜品销量TOP10_近" + range + "天_" + today.toString().replace("-", "") + ".csv");
        return t;
    }

    private SalesAnalysisVO.Table buildStallTable(List<Map.Entry<Long, AdminStallStat>> stallRank,
                                                  Map<Long, String> stallNames, int range, LocalDate today) {
        SalesAnalysisVO.Table t = new SalesAnalysisVO.Table();
        t.setColumns(List.of("排名", "档口", "订单数", "收入(元)", "退款(元)", "净收入(元)"));
        List<List<String>> rows = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<Long, AdminStallStat> e : stallRank) {
            AdminStallStat s = e.getValue();
            rows.add(List.of(String.valueOf(rank++),
                    stallNames.getOrDefault(e.getKey(), "档口#" + e.getKey()),
                    String.valueOf(s.paidOrders), money(s.revenue), money(s.refund),
                    money(s.revenue.subtract(s.refund))));
        }
        t.setRows(rows);
        t.setFilename("档口收入排行_近" + range + "天_" + today.toString().replace("-", "") + ".csv");
        return t;
    }

    private List<SalesAnalysisVO.Chart> buildCharts(List<ChartReq> reqs, List<String> dayLabels,
                                                    List<BigDecimal> dailyRevenue, List<BigDecimal> dailyOrders,
                                                    List<SalesDishVO> topDishes, Map<String, Integer> statusCounts) {
        List<SalesAnalysisVO.Chart> charts = new ArrayList<>();
        for (ChartReq req : reqs) {
            SalesAnalysisVO.Chart c = new SalesAnalysisVO.Chart();
            c.setType(req.type);
            c.setMetric(req.metric);
            c.setTitle(req.title);
            switch (req.metric) {
                case "dailyOrders" -> {
                    c.setLabels(dayLabels);
                    c.setSeries(List.of(series("订单数", dailyOrders)));
                    c.setUnit("单");
                }
                case "topDishes" -> {
                    List<String> labels = new ArrayList<>();
                    List<BigDecimal> qty = new ArrayList<>();
                    for (SalesDishVO d : topDishes) {
                        labels.add(d.getDishName());
                        qty.add(BigDecimal.valueOf(d.getQuantity() == null ? 0 : d.getQuantity()));
                    }
                    c.setLabels(labels);
                    c.setSeries(List.of(series("销量", qty)));
                    c.setUnit("份");
                }
                case "orderStatus" -> {
                    List<String> labels = new ArrayList<>();
                    List<BigDecimal> counts = new ArrayList<>();
                    for (Map.Entry<String, Integer> e : statusCounts.entrySet()) {
                        if (e.getValue() != null && e.getValue() > 0) {
                            labels.add(e.getKey());
                            counts.add(BigDecimal.valueOf(e.getValue()));
                        }
                    }
                    c.setLabels(labels);
                    c.setSeries(List.of(series("订单数", counts)));
                    c.setUnit("单");
                }
                default -> {
                    c.setType("line");
                    c.setMetric("dailyRevenue");
                    c.setLabels(dayLabels);
                    c.setSeries(List.of(series("收入", dailyRevenue)));
                    c.setUnit("元");
                }
            }
            charts.add(c);
        }
        return charts;
    }

    private SalesAnalysisVO.Table buildDailyTable(Map<LocalDate, DayStat> byDay, int range, LocalDate today) {
        SalesAnalysisVO.Table t = new SalesAnalysisVO.Table();
        t.setColumns(List.of("日期", "订单数", "已支付", "收入(元)", "客单价(元)"));
        List<List<String>> rows = new ArrayList<>();
        for (Map.Entry<LocalDate, DayStat> e : byDay.entrySet()) {
            DayStat s = e.getValue();
            BigDecimal avg = s.paid == 0 ? BigDecimal.ZERO
                    : s.revenue.divide(BigDecimal.valueOf(s.paid), 2, RoundingMode.HALF_UP);
            rows.add(List.of(e.getKey().toString(), String.valueOf(s.orders), String.valueOf(s.paid),
                    money(s.revenue), money(avg)));
        }
        t.setRows(rows);
        t.setFilename("销售明细_近" + range + "天_" + today.toString().replace("-", "") + ".csv");
        return t;
    }

    private SalesAnalysisVO.Table buildDishTable(List<SalesDishVO> topDishes, int range, LocalDate today) {
        SalesAnalysisVO.Table t = new SalesAnalysisVO.Table();
        t.setColumns(List.of("排名", "菜品", "销量(份)", "销售额(元)"));
        List<List<String>> rows = new ArrayList<>();
        int rank = 1;
        for (SalesDishVO d : topDishes) {
            rows.add(List.of(String.valueOf(rank++), d.getDishName(),
                    String.valueOf(d.getQuantity() == null ? 0 : d.getQuantity()), money(d.getAmount())));
        }
        t.setRows(rows);
        t.setFilename("菜品销售_近" + range + "天_" + today.toString().replace("-", "") + ".csv");
        return t;
    }

    private static SalesAnalysisVO.Series series(String name, List<BigDecimal> data) {
        SalesAnalysisVO.Series s = new SalesAnalysisVO.Series();
        s.setName(name);
        s.setData(data);
        return s;
    }

    private static JsonNode parseJson(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return MAPPER.readTree(raw.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private static final Set<String> ALLOWED_METRICS = Set.of(
            "dailyRevenue", "dailyOrders", "topDishes", "orderStatus");

    private static String normalizeChartType(String metric, String type) {
        String t = type == null ? "" : type.trim().toLowerCase();
        if ("orderStatus".equals(metric)) {
            return "pie";
        }
        if ("topDishes".equals(metric)) {
            return "bar";
        }
        return "bar".equals(t) ? "bar" : "line";
    }

    private static String defaultChartTitle(String metric) {
        return switch (metric) {
            case "dailyOrders" -> "每日订单数";
            case "topDishes" -> "热销菜品 TOP5";
            case "orderStatus" -> "订单状态分布";
            default -> "每日收入趋势";
        };
    }

    private static String statusLabel(String status) {
        if (status == null) {
            return "其他";
        }
        return switch (status) {
            case "PENDING" -> "待取餐";
            case "DONE" -> "已完成";
            case "REFUNDED" -> "已退款";
            case "CANCELLED" -> "已取消";
            default -> "其他";
        };
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String money(BigDecimal v) {
        return v == null ? "0.00" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static class DayStat {
        protected int orders;
        protected int paid;
        protected BigDecimal revenue = BigDecimal.ZERO;
    }

    /** 全平台分析用的日统计：在档口版之上加退款 */
    private static final class AdminDayStat extends DayStat {
        private BigDecimal refund = BigDecimal.ZERO;
    }

    private static final class AdminStallStat {
        private int paidOrders;
        private BigDecimal revenue = BigDecimal.ZERO;
        private BigDecimal refund = BigDecimal.ZERO;
    }

    private static final class ChartReq {
        private final String metric;
        private final String type;
        private final String title;

        private ChartReq(String metric, String type, String title) {
            this.metric = metric;
            this.type = type;
            this.title = title;
        }
    }
}
