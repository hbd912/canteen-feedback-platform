package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {

    public static final String TYPE_LIKE = "LIKE";
    public static final String TYPE_REPLY = "REPLY";
    public static final String TYPE_MERCHANT_REPLY = "MERCHANT_REPLY";
    // 业务事件通知（按角色推送）
    public static final String TYPE_ORDER = "ORDER";                 // 新订单 -> 商家
    public static final String TYPE_ORDER_DONE = "ORDER_DONE";       // 出餐完成 -> 学生
    public static final String TYPE_REFUND_APPLY = "REFUND_APPLY";   // 退款申请 -> 商家/管理员
    public static final String TYPE_REFUND_RESULT = "REFUND_RESULT"; // 退款结果 -> 学生
    public static final String TYPE_REVIEW = "REVIEW";               // 待回复评价 -> 商家
    public static final String TYPE_WARN = "WARN";                   // 内容违规警告 -> 学生
    public static final String TYPE_ADMIN_NOTICE = "ADMIN_NOTICE";   // 管理员提醒 -> 商家

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收者用户ID */
    private Long userId;

    /** LIKE=点赞 REPLY=学生回复 MERCHANT_REPLY=商家回复 ORDER=新订单 ... */
    private String type;

    /** 关联评价ID */
    private Long reviewId;

    /** 关联菜品ID */
    private Long dishId;

    /** 关联回复ID */
    private Long replyId;

    /** 点击跳转的前端路由 */
    private String targetUrl;

    /** 跳转后定位高亮的业务对象ID */
    private Long targetId;

    /** 通知内容摘要 */
    private String content;

    /** 0未读 1已读 */
    private Integer isRead;

    private LocalDateTime createTime;
}
