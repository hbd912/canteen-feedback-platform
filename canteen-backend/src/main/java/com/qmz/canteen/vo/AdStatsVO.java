package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class AdStatsVO {

    private Long adId;
    private String dishName;
    private String planName;
    private BigDecimal amount;
    private Integer days;
    private long impressions;
    private long clicks;
    private long conversions;
    /** 点击率 = 点击/曝光 */
    private double ctr;
    /** 转化率 = 转化/点击 */
    private double cvr;
    /** 单次点击成本 = 广告费/点击 */
    private BigDecimal costPerClick;
    private List<Daily> daily;

    @Data
    public static class Daily {
        private LocalDate date;
        private long impressions;
        private long clicks;
        private long conversions;

        public Daily() {
        }

        public Daily(LocalDate date) {
            this.date = date;
        }
    }
}
