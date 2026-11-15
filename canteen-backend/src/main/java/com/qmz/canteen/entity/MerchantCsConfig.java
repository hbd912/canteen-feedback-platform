package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家智能客服配置（一个档口一行）。
 */
@Data
@TableName("merchant_cs_config")
public class MerchantCsConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 档口ID */
    private Long stallId;

    /** 智能代回开关 0关 1开 */
    private Integer aiEnabled;

    /** 评价 AI 托管开关：自动回复待处理评价，0关 1开 */
    private Integer reviewAiEnabled;

    /** 商家自定义人设/话术 */
    private String persona;

    /** 自动欢迎语 */
    private String greeting;

    /** AI 老顾客安抚发券开关 0关 1开 */
    private Integer discountEnabled;

    /** 商家可手动发券 0关 1开 */
    private Integer manualGrantEnabled;

    /** 最低折扣（0.50=五折） */
    private BigDecimal minRate;

    /** 最高折扣（0.95=九五折） */
    private BigDecimal maxRate;

    /** 允许免单（0折）0关 1开 */
    private Integer allowFree;

    /** 同一学生同一档口发券冷却天数 */
    private Integer cooldownDays;

    /** 老客门槛：本档口已完成订单数 */
    private Integer veteranMinOrders;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
