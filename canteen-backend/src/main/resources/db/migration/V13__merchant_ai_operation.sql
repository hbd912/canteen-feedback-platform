-- ============================================================
-- V13 AI 经营托管：真实订单诊断 + 下架建议人工审批闭环
-- merchant_operation_action 记录 AI 生成的经营建议动作，
-- 只做「建议 → 人工审批 → 服务端执行」的安全闭环。
-- 不自动改价、不自动上架、不退款、不碰订单状态。
-- ============================================================

CREATE TABLE IF NOT EXISTS merchant_operation_action (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    stall_id       BIGINT        NOT NULL COMMENT '档口ID',
    action_type    VARCHAR(40)   NOT NULL COMMENT '动作类型：DISH_OFF_SHELF',
    dish_id        BIGINT        NULL COMMENT '关联菜品ID',
    dish_name      VARCHAR(120)  NULL COMMENT '菜品名快照',
    expected_status TINYINT      NULL COMMENT '建议时的菜品状态',
    target_status  TINYINT       NULL COMMENT '目标状态',
    reason         VARCHAR(500)  NULL COMMENT 'AI 建议理由',
    evidence_json  TEXT          NULL COMMENT '证据快照(销量/评分/差评)',
    status         VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/EXECUTED/REJECTED/FAILED',
    model_name     VARCHAR(60)   NULL COMMENT '生成模型',
    created_by     VARCHAR(40)   NULL COMMENT '创建来源(AI/商家)',
    approved_by    BIGINT        NULL COMMENT '审批人用户ID',
    approved_time  DATETIME      NULL,
    executed_time  DATETIME      NULL,
    error_message  VARCHAR(500)  NULL,
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_stall_status (stall_id, status),
    KEY idx_dish (dish_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 经营托管动作';
