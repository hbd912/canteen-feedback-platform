package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 宝藏档口：点单数、评分、卫生分三项折算后加权得到综合分
 */
@Data
public class StallTreasureVO {

    private Long stallId;
    private String stallName;
    private String windowNo;
    private String location;

    /** 档口所在区域楼层，如「南区 1 楼」 */
    private String stallZone;

    /** 档口门头/封面图，双龙榜名牌展示 */
    private String coverImage;

    private BigDecimal hygieneScore;
    private Double avgRating;
    private Long reviewCount;
    private Long dishCount;

    /** 该档口全部菜品累计点单数 */
    private Long salesTotal;

    /** 综合分（百分制） */
    private Double composite;
}
