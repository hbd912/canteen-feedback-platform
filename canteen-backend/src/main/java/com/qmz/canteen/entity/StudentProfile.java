package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生 AI 画像：助手根据对话与消费记录总结出的偏好/情绪/习惯，
 * 供助手个性化回答（my_preferences 工具）与管理端查看（用户与商家页抽屉）。
 */
@Data
@TableName("student_profile")
public class StudentProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 口味偏好标签，逗号分隔（如 偏辣,爱甜,清淡） */
    private String tasteTags;

    /** 常点档口名，逗号分隔，按频次降序 */
    private String favoriteStalls;

    /** 常点菜品名，逗号分隔，按频次降序 */
    private String favoriteDishes;

    /** 最近7天消费（冗余快照，画像刷新时更新） */
    private BigDecimal expenseWeek;

    /** 最近30天消费 */
    private BigDecimal expenseMonth;

    /** 累计消费 */
    private BigDecimal expenseTotal;

    /** 累计订单数 */
    private Integer orderCount;

    /** 最近情绪记录摘要 */
    private String moodNotes;

    /** AI 生成的画像总结（管理端与助手共用） */
    private String aiSummary;

    /** AI 总结生成时间 */
    private LocalDateTime aiSummaryAt;

    /** 累计对话轮数（画像置信度） */
    private Integer msgCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
