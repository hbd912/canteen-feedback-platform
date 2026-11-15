package com.qmz.canteen.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MerchantAssistantRequest {

    /** 问题文本；仅传图（上传菜品）时可为空，Service 会用默认提示补上 */
    @Size(max = 500, message = "问题不能超过500字")
    private String question;

    /** 当前技能：upload（上传菜品）/ chat（经营问答）/ sales（销售分析）。决定 Agent 的角色提示词与工具 */
    @Size(max = 20, message = "技能名过长")
    private String skill;

    /** 可选：商家上传的菜品图片路径（本地路径，如 /uploads/xxx.jpg 或 /dishes/1.jpg），有图时走视觉识别 */
    @Size(max = 500, message = "图片路径过长")
    private String image;
}
