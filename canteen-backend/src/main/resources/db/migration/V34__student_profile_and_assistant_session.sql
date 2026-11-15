-- ============================================================
-- V34 学生 AI 画像 + 小助手会话持久化：
-- 1) student_profile    学生画像（口味/消费快照/AI 总结），供助手个性化 + 管理端查看
-- 2) assistant_session  学生与食堂小助手的会话，刷新/换设备不丢
-- 3) assistant_message  会话消息（answer_json 存 ChatAnswerVO 快照，卡片可完整还原）
-- 幂等：可重复执行（IF NOT EXISTS）
-- ============================================================

CREATE TABLE IF NOT EXISTS student_profile (
    id            BIGINT      AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id       BIGINT      NOT NULL COMMENT '学生用户ID',
    taste_tags    VARCHAR(500) NULL COMMENT '口味偏好标签，逗号分隔（如 偏辣,爱甜,清淡）',
    favorite_stalls VARCHAR(300) NULL COMMENT '常点档口名，逗号分隔，按频次排序',
    favorite_dishes VARCHAR(300) NULL COMMENT '常点菜品名，逗号分隔，按频次排序',
    expense_week  DECIMAL(10,2) NULL COMMENT '最近7天消费（冗余快照）',
    expense_month DECIMAL(10,2) NULL COMMENT '最近30天消费（冗余快照）',
    expense_total DECIMAL(10,2) NULL COMMENT '累计消费（冗余快照）',
    order_count   INT         NULL COMMENT '累计订单数（冗余快照）',
    mood_notes    VARCHAR(500) NULL COMMENT '最近情绪记录摘要（如 期中压力大想吃辣）',
    ai_summary    VARCHAR(1000) NULL COMMENT 'AI 生成的画像总结（给管理端和助手看）',
    ai_summary_at DATETIME    NULL COMMENT 'AI 总结生成时间',
    msg_count     INT         NOT NULL DEFAULT 0 COMMENT '累计对话轮数（画像置信度）',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学生AI画像表';

CREATE TABLE IF NOT EXISTS assistant_session (
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id    BIGINT      NOT NULL COMMENT '学生用户ID',
    title      VARCHAR(60) NULL COMMENT '会话标题（首条问题截断）',
    create_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    deleted    TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    KEY idx_user (user_id, update_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '小助手会话表';

CREATE TABLE IF NOT EXISTS assistant_message (
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    session_id BIGINT      NOT NULL COMMENT '会话ID',
    role       VARCHAR(12) NOT NULL COMMENT 'user / assistant',
    content    TEXT        NULL COMMENT '消息文本',
    answer_json TEXT       NULL COMMENT '助手消息的完整 ChatAnswerVO JSON（卡片/快捷回复原样恢复）',
    create_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_session (session_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '小助手消息表';
