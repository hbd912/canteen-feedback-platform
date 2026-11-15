package com.qmz.canteen.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CsConfigRequest {

    /** 智能代回开关 0关 1开 */
    private Integer aiEnabled;

    /** 评价 AI 托管开关 0关 1开 */
    private Integer reviewAiEnabled;

    /** 商家自定义人设/话术 */
    @Size(max = 500, message = "人设不能超过500字")
    private String persona;

    /** 自动欢迎语 */
    @Size(max = 500, message = "欢迎语不能超过500字")
    private String greeting;

    /** AI 老顾客安抚发券开关 0关 1开 */
    private Integer discountEnabled;

    /** 商家可手动发券 0关 1开 */
    private Integer manualGrantEnabled;

    /** 最低折扣（0.50=五折） */
    @DecimalMin(value = "0.10", message = "最低折扣不能低于一折")
    @DecimalMax(value = "1.00", message = "最低折扣不能高于原价")
    private BigDecimal minRate;

    /** 最高折扣（0.95=九五折） */
    @DecimalMin(value = "0.10", message = "最高折扣不能低于一折")
    @DecimalMax(value = "1.00", message = "最高折扣不能高于原价")
    private BigDecimal maxRate;

    /** 允许免单（0折）0关 1开 */
    private Integer allowFree;

    /** 同一学生同一档口发券冷却天数 */
    @Min(value = 0, message = "冷却天数不能为负")
    @Max(value = 365, message = "冷却天数最多365天")
    private Integer cooldownDays;

    /** 老客门槛：本档口已完成订单数 */
    @Min(value = 1, message = "老客门槛至少1单")
    @Max(value = 999, message = "老客门槛最多999单")
    private Integer veteranMinOrders;
}