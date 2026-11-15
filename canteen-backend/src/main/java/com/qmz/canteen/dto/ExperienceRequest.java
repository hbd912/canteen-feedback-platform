package com.qmz.canteen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExperienceRequest {

    @NotBlank(message = "场景不能为空")
    @Size(max = 60, message = "场景过长")
    private String scene;

    @NotBlank(message = "问题不能为空")
    @Size(max = 1000, message = "问题过长")
    private String problem;

    @NotBlank(message = "解法不能为空")
    @Size(max = 2000, message = "解法过长")
    private String solution;

    @Size(max = 200, message = "标签过长")
    private String tags;

    @Min(value = 1, message = "评分最低 1")
    @Max(value = 5, message = "评分最高 5")
    private Integer score = 5;

    /** 可选：归属档口；为空表示全场通用 */
    private Long stallId;
}
