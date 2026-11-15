package com.qmz.canteen.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家待办聚合项：把「新订单 / 待退款 / 客服未读 / 待回复评价」与
 * 已有的整改敦促、差评预警统一成一条可点击跳转的待办卡片。
 */
@Data
public class MerchantTodoVO {

    /** 事件类型：ORDER / REFUND / MESSAGE / REVIEW / BAD_REVIEW / INSPECT */
    private String type;

    /** 类型中文标签 */
    private String typeLabel;

    /** 卡片标题 */
    private String title;

    /** 内容摘要（最新一条的预览） */
    private String content;

    /** 待处理数量 */
    private Integer count;

    /** 前端跳转路由 */
    private String targetUrl;

    /** 跳转后定位高亮的对象 id（订单/退款/会话/评价/菜品 id） */
    private Long targetId;

    /** 最新事件时间 */
    private LocalDateTime latestTime;

    /** 可「标记已处理」的待办 id（仅 INSPECT/BAD_REVIEW 有值，其余为 null） */
    private Long doneId;
}
