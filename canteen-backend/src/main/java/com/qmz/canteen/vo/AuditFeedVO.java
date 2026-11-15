package com.qmz.canteen.vo;

import lombok.Data;

import java.util.List;

@Data
public class AuditFeedVO {

    private List<AuditItemVO> records;
    private long total;
    private int current;
    private int size;
}
