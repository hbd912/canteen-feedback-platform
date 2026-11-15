package com.qmz.canteen.vo;

import lombok.Data;

/** 管理端待办聚合项 */
@Data
public class AdminTodoVO {

    /** CERT 资质审核 / REFUND 退款 / REVIEW 评价 / MESSAGE 留言 */
    private String type;

    private String title;

    private String content;

    private long count;

    /** 点击待办跳转的前端路由 */
    private String targetUrl;
}