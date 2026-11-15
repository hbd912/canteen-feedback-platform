package com.qmz.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssistantFeedbackRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题过长")
    private String question;

    @NotBlank(message = "回答不能为空")
    @Size(max = 2000, message = "回答过长")
    private String answer;

    /** true=这条建议有用，写入经验库 */
    private Boolean helpful;
}
