package com.qmz.canteen.vo;

import lombok.Data;

import java.util.List;

/**
 * 商家档口顾客频次统计：按学生在本档口的已完成订单数分层（新客/常客/老客），
 * 让老板一眼看清熟客占比，配合客服 AI 的老顾客安抚发券。
 */
@Data
public class StallCustomerStatsVO {

    /** 去过本档口的学生总数（去重） */
    private long totalStudents;

    /** 本档口已完成(DONE)订单总数 */
    private long totalDoneOrders;

    /** 新客人数（已完成 <3 单） */
    private long newCount;
    /** 常客人数（已完成 3-9 单） */
    private long regularCount;
    /** 老客人数（已完成 ≥10 单） */
    private long veteranCount;

    /** 已通过客服 AI 发放的安抚券总数（本档口） */
    private long discountsGranted;

    /** 学生明细（按单量从多到少），供列表展示 */
    private List<StudentFreqItem> students;

    @Data
    public static class StudentFreqItem {
        private Long userId;
        private String studentName;
        private String studentAvatar;
        /** 已完成单量 */
        private long doneCount;
        /** 新客/常客/老客 */
        private String tier;
    }
}