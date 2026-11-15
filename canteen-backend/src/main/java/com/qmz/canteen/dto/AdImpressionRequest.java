package com.qmz.canteen.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AdImpressionRequest {

    @NotEmpty(message = "广告ID不能为空")
    @Size(max = 20, message = "单次最多上报20条")
    private List<Long> adIds;
}
