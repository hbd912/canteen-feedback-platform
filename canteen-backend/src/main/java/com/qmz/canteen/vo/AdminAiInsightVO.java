package com.qmz.canteen.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 管理端工作台的 AI 值班助理简报。 */
@Data
public class AdminAiInsightVO {

    /** EMPTY 尚未分析 / READY 有结果 / ERROR 分析失败。 */
    private String status = "EMPTY";

    private String summary;
    private String aiNote;
    private LocalDateTime generatedAt;
    private int windowDays = 7;
    /** 当前待处理总量。 */
    private int totalItems;
    /** 本次实际参与研判的条目数；数据量大时只取最近的若干条。 */
    private int analyzedItems;
    private boolean truncated;
    private int needActionCount;
    private int sootheCount;
    private int observeCount;
    private int prevalentCount;
    /** 本次已自动回复并标记已处理的条数 */
    private int autoHandledCount;
    /** 是否执行了自动处理（false=只读模式） */
    private boolean autoHandled;
    private boolean degraded;
    private boolean stale;
    private String model;
    private long costMs;

    private List<Coverage> coverage = new ArrayList<>();
    /** 需要人工确认的敏感项 */
    private List<AdminAiFindingVO> needAction = new ArrayList<>();
    /** 可以自动处理、免审核的条目 */
    private List<AdminAiFindingVO> autoHandleable = new ArrayList<>();
    private int autoHandleableCount;
    private List<AdminAiFindingVO> soothe = new ArrayList<>();
    private List<AdminAiFindingVO> observe = new ArrayList<>();
    /** 已自动处理的条目（前端折叠展示「已自动处理 N 条」） */
    private List<AdminAiFindingVO> autoHandledItems = new ArrayList<>();

    /** 处理明细：处理了什么、怎么处理的、回了什么 */
    private List<HandledDetail> handledDetails = new ArrayList<>();

    @Data
    public static class HandledDetail {
        /** FEEDBACK / REVIEW / MERCHANT_MESSAGE / DISH_AUDIT */
        private String sourceType;
        private String sourceLabel;
        private String title;
        /** 处理动作：已回复并标记处理 / 已审核通过 / 已驳回 / 已标记处理 */
        private String action;
        /** 回复内容（若有） */
        private String reply;
        private int count;
    }

    /** 严重食品安全/卫生问题时的公告建议（前端可授权发布），无建议时为 null */
    private String announcementTitle;
    private String announcementContent;
    private boolean announcementSuggested;

    @Data
    public static class Coverage {
        private String source;
        private String label;
        private int count;

        public Coverage() {
        }

        public Coverage(String source, String label, int count) {
            this.source = source;
            this.label = label;
            this.count = count;
        }
    }
}
