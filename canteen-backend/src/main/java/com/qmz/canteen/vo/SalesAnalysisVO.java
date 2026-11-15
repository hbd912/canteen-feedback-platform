package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 销售分析：AI 只决定展示哪些图表和解读，图表/表格的数据全部来自后端真实查询。
 */
@Data
public class SalesAnalysisVO {

    private String title;
    private String summary;
    private List<String> insights;
    private List<Chart> charts;
    /** 按日销售明细（可下载） */
    private Table table;
    /** 按菜品销售明细（可下载） */
    private Table dishTable;
    /** 按档口排行（仅全平台分析有值，可下载） */
    private Table stallTable;
    /** 按月汇总（仅长周期 ≥28 天有值，可下载） */
    private Table monthTable;
    private String model;
    private LocalDateTime generatedAt;
    /** 统计区间天数 */
    private Integer days;

    @Data
    public static class Chart {
        /** line / bar / pie */
        private String type;
        private String title;
        /** dailyRevenue / dailyOrders / topDishes / orderStatus */
        private String metric;
        private List<String> labels;
        private List<Series> series;
        private String unit;
    }

    @Data
    public static class Series {
        private String name;
        private List<BigDecimal> data;
    }

    @Data
    public static class Table {
        private List<String> columns;
        private List<List<String>> rows;
        private String filename;
    }
}
