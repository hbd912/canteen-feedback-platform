package com.qmz.canteen.vo;

import lombok.Data;

import java.util.List;

/**
 * 美食双龙榜：左列按点单数取热销菜品，右列按综合分取宝藏档口
 */
@Data
public class DragonBoardVO {

    private List<DishRankVO> hotDishes;

    private List<StallTreasureVO> treasureStalls;

    /** 入榜所需最少评价数 */
    private Integer minReviews;

    /** 综合分权重说明，供前端直接展示 */
    private String formula;
}
