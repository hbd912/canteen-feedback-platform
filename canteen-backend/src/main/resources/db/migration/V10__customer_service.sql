-- ============================================================
-- V10 商家客服系统：学生<->商家会话、智能代回、快捷回复
-- 1) customer_service_session  客服会话（一个学生对一个档口一条会话）
-- 2) customer_service_message  客服消息（STUDENT/MERCHANT/AI）
-- 3) merchant_cs_config        商家智能客服配置（开关/人设/欢迎语）
-- 4) merchant_quick_reply      商家快捷回复库
-- ============================================================

-- 1. 客服会话表
CREATE TABLE IF NOT EXISTS customer_service_session (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    stall_id          BIGINT        NOT NULL COMMENT '档口ID',
    user_id           BIGINT        NOT NULL COMMENT '学生ID',
    order_id          BIGINT        NULL COMMENT '关联订单ID（可为空）',
    last_message      VARCHAR(500)  NULL COMMENT '最后一条消息摘要',
    last_message_time DATETIME      NULL COMMENT '最后消息时间',
    student_unread    INT           NOT NULL DEFAULT 0 COMMENT '学生未读数',
    merchant_unread   INT           NOT NULL DEFAULT 0 COMMENT '商家未读数',
    status            VARCHAR(20)   NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN会话中 CLOSED已结束',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_stall (stall_id),
    KEY idx_user (user_id),
    KEY idx_stall_user (stall_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客服会话';

-- 2. 客服消息表
CREATE TABLE IF NOT EXISTS customer_service_message (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    session_id  BIGINT         NOT NULL COMMENT '会话ID',
    sender_type VARCHAR(10)    NOT NULL COMMENT 'STUDENT学生 MERCHANT商家 AI小助手',
    sender_id   BIGINT         NULL COMMENT '发送者用户ID（AI消息为NULL）',
    content     VARCHAR(1000)  NOT NULL COMMENT '消息内容',
    create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_session (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客服消息';

-- 3. 商家智能客服配置表（一个档口一行）
CREATE TABLE IF NOT EXISTS merchant_cs_config (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    stall_id    BIGINT        NOT NULL COMMENT '档口ID',
    ai_enabled  TINYINT       NOT NULL DEFAULT 0 COMMENT '智能代回开关 0关 1开',
    persona     VARCHAR(500)  NULL COMMENT '商家自定义人设/话术',
    greeting    VARCHAR(500)  NULL COMMENT '自动欢迎语',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stall (stall_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商家智能客服配置';

-- 4. 商家快捷回复库
CREATE TABLE IF NOT EXISTS merchant_quick_reply (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    stall_id    BIGINT        NOT NULL COMMENT '档口ID',
    content     VARCHAR(500)  NOT NULL COMMENT '快捷回复内容',
    sort        INT           NOT NULL DEFAULT 0 COMMENT '排序权重',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_stall (stall_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商家快捷回复';
