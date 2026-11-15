package com.qmz.canteen.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服会话视图：学生侧看档口名，商家侧看学生名。
 */
@Data
public class CustomerServiceSessionVO {

    private Long id;
    private Long stallId;
    private String stallName;
    /** 商家账号头像（档口 logo），AI 代回也沿用此身份 */
    private String stallAvatar;
    private Long userId;
    private String studentName;
    private String studentAvatar;
    private Long orderId;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private Integer studentUnread;
    private Integer merchantUnread;
    private String status;
    private LocalDateTime createTime;
}
