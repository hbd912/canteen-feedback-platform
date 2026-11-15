package com.qmz.canteen.service;

import com.qmz.canteen.vo.StudentProfileVO;

/**
 * 学生 AI 画像：从订单/评价/收藏/聊天日志里聚合偏好与消费习惯，
 * 让 LLM 异步总结成画像；助手回答时带上画像，管理端可视化查看。
 */
public interface StudentProfileService {

    /**
     * 读取画像（带轻量缓存：同请求内不重复聚合）。不存在时聚合一次并落库。
     * 只读路径：助手 my_preferences 工具与管理端抽屉共用。
     */
    StudentProfileVO getProfile(Long userId);

    /**
     * 记录一轮对话信号：情绪词命中时追加 mood_notes，对话轮数 +1。
     * 达到阈值（每 N 轮）后触发一次画像刷新。
     *
     * @param moodHit 本轮对话是否命中情绪词
     * @param moodWord 命中的情绪关键词（null 表示无）
     * @return 本次对话后累计的未刷新轮数
     */
    int recordChatTurn(Long userId, boolean moodHit, String moodWord);

    /** 立即刷新画像（聚合数据 + LLM 总结）。LLM 失败时只更新统计字段，不抛异常。 */
    void refreshProfile(Long userId);

    /** 管理端：按用户读画像（不聚合新数据，仅读已有 + 必要时补统计） */
    StudentProfileVO adminGetProfile(Long userId);
}
