-- ============================================================
-- V14 AI 时代的数据底座 + 事件日志 + 经验库
-- 1) dish 增加库存/售罄字段（AI 判断「快售罄 / 自动下架」的前提）
-- 2) coupon / user_coupon 优惠券（营销规则的前提）
-- 3) refund_request 退款占位（当前只记录与审批，不接支付网关退款）
-- 4) ai_event_log 事件日志（大脑每次判断与结果的原料）
-- 5) ai_experience 经验库（成功案例/人工纠正，供 RAG 检索）
-- ============================================================

ALTER TABLE dish
    ADD COLUMN stock_qty INT NULL COMMENT '每日库存，NULL=不限' AFTER status,
    ADD COLUMN sold_out TINYINT NOT NULL DEFAULT 0 COMMENT '是否售罄 0否 1是' AFTER stock_qty;

CREATE TABLE IF NOT EXISTS coupon (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    stall_id       BIGINT        NULL COMMENT '归属档口，NULL=平台券',
    name           VARCHAR(120)  NOT NULL COMMENT '券名',
    type           VARCHAR(20)   NOT NULL DEFAULT 'AMOUNT' COMMENT 'AMOUNT满减 DISCOUNT折扣',
    value          DECIMAL(10,2) NOT NULL COMMENT '面额或折扣(0.9=九折)',
    min_amount     DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '最低消费',
    total_qty      INT           NOT NULL DEFAULT 0 COMMENT '发放总量，0=不限',
    issued_qty     INT           NOT NULL DEFAULT 0 COMMENT '已发放',
    per_user_limit INT           NOT NULL DEFAULT 1 COMMENT '每人限领',
    start_time     DATETIME      NULL,
    end_time       DATETIME      NULL,
    status         TINYINT       NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_by     BIGINT        NULL COMMENT '创建人',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_stall (stall_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '优惠券';

CREATE TABLE IF NOT EXISTS user_coupon (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    coupon_id    BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'UNUSED' COMMENT 'UNUSED/USED/EXPIRED',
    receive_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    use_time     DATETIME    NULL,
    order_id     BIGINT      NULL,
    deleted      TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user (user_id, status),
    KEY idx_coupon (coupon_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户优惠券';

CREATE TABLE IF NOT EXISTS refund_request (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    order_id     BIGINT        NOT NULL,
    user_id      BIGINT        NOT NULL,
    stall_id     BIGINT        NOT NULL,
    amount       DECIMAL(10,2) NOT NULL,
    reason       VARCHAR(500)  NULL,
    status       VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/REFUNDED',
    handled_by   BIGINT        NULL,
    handled_time DATETIME      NULL,
    remark       VARCHAR(500)  NULL,
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_order (order_id),
    KEY idx_stall_status (stall_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '退款申请（占位，暂不接支付网关）';

CREATE TABLE IF NOT EXISTS ai_event_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    event_type    VARCHAR(40)  NOT NULL COMMENT '事件类型',
    source        VARCHAR(20)  NULL COMMENT 'STUDENT/MERCHANT/ADMIN/SYSTEM',
    source_id     BIGINT       NULL COMMENT '来源业务ID',
    user_id       BIGINT       NULL,
    stall_id      BIGINT       NULL,
    content       VARCHAR(2000) NULL COMMENT '事件内容(脱敏后)',
    decision_json VARCHAR(1000) NULL COMMENT '大脑决策JSON',
    assign_to     VARCHAR(40)  NULL COMMENT '分配角色',
    need_human    TINYINT      NULL COMMENT '是否需要人工',
    status        VARCHAR(20)  NOT NULL DEFAULT 'DONE' COMMENT 'DONE/FAILED/ESCALATED',
    result        VARCHAR(2000) NULL COMMENT '处理结果摘要',
    model_name    VARCHAR(80)  NULL COMMENT '实际使用的模型',
    tokens        INT          NULL COMMENT '消耗token(估算)',
    cost_ms       INT          NULL COMMENT '耗时',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_stall_time (stall_id, create_time),
    KEY idx_type_time (event_type, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 事件日志';

CREATE TABLE IF NOT EXISTS ai_experience (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    scene       VARCHAR(60)  NOT NULL COMMENT '场景',
    problem     VARCHAR(1000) NOT NULL COMMENT '问题',
    solution    VARCHAR(2000) NOT NULL COMMENT '有效解法',
    outcome     VARCHAR(20)  NULL COMMENT 'SUCCESS/FAILED/CORRECTED',
    score       INT          NULL COMMENT '人工评分1-5',
    tags        VARCHAR(200) NULL,
    stall_id    BIGINT       NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_scene (scene),
    KEY idx_stall (stall_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 经验库';
