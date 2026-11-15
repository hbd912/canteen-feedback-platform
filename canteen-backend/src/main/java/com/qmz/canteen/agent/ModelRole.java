package com.qmz.canteen.agent;

/**
 * 模型角色：按「任务性质」选模型，而不是按「Agent 名字」选模型。
 */
public enum ModelRole {

    /** 大脑：判断/路由/JSON 决策，走智谱 glm-4-flash，稳定优先 */
    BRAIN,

    /** 快任务：客服话术、评价回复、经营问答，走硅基 Qwen2.5-7B */
    FAST,

    /** 难任务：代码分析、经营诊断、复杂纠纷，走硅基 DeepSeek-R1-8B */
    REASONING,

    /** 视觉：菜品图片理解，走 scnet GLM-5.3-Flash（备用 Qwen3-VL-8B/30B） */
    VISION,

    /** 报告：经营简报/周期性分析，走 scnet GLM-5.3-Flash（关闭长思考） */
    REPORT
}
