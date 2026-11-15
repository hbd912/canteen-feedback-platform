package com.qmz.canteen.vo;

import lombok.Data;

import java.util.List;

@Data
public class AdStatsOverviewVO {

    private long impressions;
    private long clicks;
    private long conversions;
    private double ctr;
    private double cvr;
    private List<AdStatsVO> ads;
}
