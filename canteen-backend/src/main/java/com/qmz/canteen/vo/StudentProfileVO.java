package com.qmz.canteen.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生 AI 画像视图：助手 my_preferences 工具与管理端「AI 画像」抽屉共用。
 */
@Data
public class StudentProfileVO {

    private Long userId;

    /** 学生姓名/账号（管理端展示用） */
    private String realName;
    private String username;
    private String studentNo;

    /** 口味偏好标签，逗号分隔（偏辣,爱甜,清淡…） */
    private String tasteTags;

    /** 常点档口（按频次降序） */
    private String favoriteStalls;

    /** 常点菜品（按频次降序） */
    private String favoriteDishes;

    /** 最近7天消费 */
    private BigDecimal expenseWeek;
    /** 最近30天消费 */
    private BigDecimal expenseMonth;
    /** 累计消费 */
    private BigDecimal expenseTotal;
    /** 累计订单数 */
    private Integer orderCount;

    /** 情绪记录摘要（最近若干条） */
    private String moodNotes;

    /** AI 生成的画像总结 */
    private String aiSummary;
    /** AI 总结时间 */
    private LocalDateTime aiSummaryAt;

    /** 累计对话轮数 */
    private Integer msgCount;

    /** 常去时段画像（如 午高峰/晚高峰），来自订单时间分布 */
    private String activeHours;
}
