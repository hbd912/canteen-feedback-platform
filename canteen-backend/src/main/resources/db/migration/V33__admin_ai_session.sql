-- ============================================================
-- V33 管理员工作台 AI 助手会话持久化：
-- 1) admin_ai_session   管理员与值班助理的会话（一人多会话）
-- 2) admin_ai_message   会话消息（USER/AI，content 存前端消息 JSON 快照，
--                        insight 卡片/图表等富内容可完整还原）
-- ============================================================

CREATE TABLE IF NOT EXISTS admin_ai_session (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    user_id           BIGINT        NOT NULL COMMENT '管理员用户ID',
    title             VARCHAR(100)  NULL COMMENT '会话标题（取首条用户消息）',
    last_message      VARCHAR(500)  NULL COMMENT '最后一条消息摘要',
    last_message_time DATETIME      NULL COMMENT '最后消息时间',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_user (user_id, update_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员工作台AI会话';

CREATE TABLE IF NOT EXISTS admin_ai_message (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    session_id  BIGINT         NOT NULL COMMENT '会话ID',
    sender_type VARCHAR(10)    NOT NULL COMMENT 'USER管理员 AI助理',
    content     MEDIUMTEXT     NOT NULL COMMENT '前端消息对象 JSON 快照（含 insight 卡片/图表数据）',
    create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_session (session_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员工作台AI消息';
