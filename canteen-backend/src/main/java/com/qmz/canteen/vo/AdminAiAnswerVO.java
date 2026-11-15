package com.qmz.canteen.vo;

import lombok.Data;

/** 工作台值班助理对管理员追问的回答。 */
@Data
public class AdminAiAnswerVO {

    private String answer;
    private String model;
    private long costMs;
    private boolean degraded;
}
