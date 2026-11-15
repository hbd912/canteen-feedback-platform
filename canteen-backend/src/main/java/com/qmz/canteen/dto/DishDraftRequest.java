package com.qmz.canteen.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 菜品草稿请求：图片可选、商家提示可选。
 * 价格由商家定，AI 只生成菜名/分类/描述草稿，不替商家定价。
 */
@Data
public class DishDraftRequest {

    @Size(max = 500, message = "图片地址过长")
    private String image;

    @Size(max = 60, message = "菜名过长")
    private String name;

    @DecimalMin(value = "0", message = "价格不能为负")
    private BigDecimal price;

    @Size(max = 30, message = "分类过长")
    private String category;

    /** 商家补充的口味/卖点/食材提示，例如「微辣、分量足、有花生」 */
    @Size(max = 200, message = "补充描述不能超过200字")
    private String hint;

    /** 商家对上一版草稿不满意时的反馈（打回重做用），例如「名字太怪，偏辣一点」 */
    @Size(max = 200, message = "反馈不能超过200字")
    private String feedback;

    /* 上一版草稿（打回重做时带上，AI 在原基础上调整而不是凭空重编） */
    @Size(max = 60, message = "菜名过长")
    private String previousName;

    @Size(max = 30, message = "分类过长")
    private String previousCategory;

    @Size(max = 500, message = "描述过长")
    private String previousDescription;

    /** 上一版价格（打回重做时沿用，AI 没给新价时兜底） */
    @DecimalMin(value = "0", message = "价格不能为负")
    private BigDecimal previousPrice;
}
