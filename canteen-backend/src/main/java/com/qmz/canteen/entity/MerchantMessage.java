package com.qmz.canteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("merchant_message")
public class MerchantMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发送方用户ID（商家消息必有；管理员消息可空） */
    private Long userId;

    private Long stallId;

    /** 方向：IN 商家→管理员 / OUT 管理员→商家 */
    private String direction;

    /** 发送方角色：MERCHANT / ADMIN / AI */
    private String authorRole;

    /** 1=置顶通知抬头（AI/系统生成，列表置顶高亮） */
    private Integer notice;

    /** 通知/反馈标题（可空） */
    private String title;

    private String content;

    private Integer status;

    private String reply;

    private LocalDateTime replyTime;

    private LocalDateTime createTime;

    // ---- 常量 ----
    public static final String DIR_IN = "IN";
    public static final String DIR_OUT = "OUT";
    public static final String AUTHOR_MERCHANT = "MERCHANT";
    public static final String AUTHOR_ADMIN = "ADMIN";
    public static final String AUTHOR_AI = "AI";
}
