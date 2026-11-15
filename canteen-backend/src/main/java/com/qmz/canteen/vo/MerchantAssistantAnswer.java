package com.qmz.canteen.vo;

import lombok.Data;

import java.util.List;

@Data
public class MerchantAssistantAnswer {

    private String answer;
    private String model;
    private Integer tokens;
    private Long costMs;
    /** 本次回答依据的数据快照，便于商家核对 AI 有没有瞎说 */
    private String dataSnapshot;
    /** 本次回答检索到的经验条数 */
    private Integer experienceUsed;
    /** 是否使用了视觉模型识别商家上传的图片 */
    private Boolean visionUsed;
    /** 当商家在对话中要求分析/表格时，Agent 附带生成的真实图表（来自后端统计，非 AI 编造） */
    private List<SalesAnalysisVO.Chart> charts;
    /** 同一轮生成的真实数据表格（按日 + 按菜品），可下载 CSV */
    private List<SalesAnalysisVO.Table> tables;
    /** 本次附带数据的统计区间天数 */
    private Integer dataDays;
    /** 当商家在「上传菜品」技能里只传图片/描述时，Agent 直接返回上架草稿卡片（商家确认后才建） */
    private DishDraftVO draft;
    /** 当前识别到的技能（upload/chat/sales），方便前端提示 */
    private String detectedSkill;
}
