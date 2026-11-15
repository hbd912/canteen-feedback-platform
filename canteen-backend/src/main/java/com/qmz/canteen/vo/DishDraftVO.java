package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 菜品上架草稿：商家确认后才真正创建菜品。
 */
@Data
public class DishDraftVO {

    private String name;
    private String category;
    private String description;
    /** 商家填的价格优先；没填时为 AI 建议价 */
    private BigDecimal price;

    /** 价格是否为 AI 建议（商家没填价时 true） */
    private Boolean priceSuggested;
    /** 是否真的看了图片 */
    private Boolean visionUsed;
    private String model;
    private Integer tokens;
    /** 给商家的提示，例如「视觉模型不可用，已按文字生成」 */
    private String note;
}
