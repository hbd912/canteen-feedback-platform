package com.qmz.canteen.service;

import com.qmz.canteen.vo.AdminAiAnswerVO;
import com.qmz.canteen.vo.AdminAiInsightVO;

/** 管理端工作台 AI 值班助理：只做聚合研判与草稿，不执行资金/处罚动作。 */
public interface AdminAiInsightService {

    /** 返回最近一次缓存；没有缓存时返回 EMPTY，不触发模型调用。 */
    AdminAiInsightVO getCached();

    /** 重新聚合当前留言、评价、商家留言和退款，可调用模型生成摘要与草稿。 */
    AdminAiInsightVO refresh();

    /** 管理员明确「不用确认，直接处理」：把可代处理的敏感条目一并处理，退款/P0 仍留人工。 */
    AdminAiInsightVO handleAll();

    /** 管理员选「只处理免审核的」：只自动处理可回复的非敏感项，敏感项仍列出来人工定位。 */
    AdminAiInsightVO handleAutoOnly();

    /** 基于当前研判回答管理员追问；没有缓存时先整理一次。 */
    AdminAiAnswerVO ask(String question);

    /** 值班助理开场白：根据当前待办统计由模型动态生成。 */
    String greeting();
}
