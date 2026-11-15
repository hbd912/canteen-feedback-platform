package com.qmz.canteen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CustomerServiceRecordQuery {

    @Pattern(regexp = "(CHAT|SESSION)?", message = "记录类型不合法")
    private String kind;

    private String keyword;

    @Pattern(regexp = "(ATTENTION)?", message = "记录状态不合法")
    private String state;

    @Min(value = 1, message = "页码不能小于1")
    private int current = 1;

    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数最多100条")
    private int size = 10;
}
