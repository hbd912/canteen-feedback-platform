package com.qmz.canteen.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerServiceRecordVO {

    private Long id;
    private String kind;
    private String question;
    private String person;
    private String stallName;
    private String status;
    private Integer hit;
    private Integer helpful;
    private Integer costMs;
    private String intentLabel;
    private Integer merchantUnread;
    private Long orderId;
    private LocalDateTime createTime;
}
