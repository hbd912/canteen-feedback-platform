-- ============================================================
-- V16 菜品广告投放（付费置顶）
-- 规则：商家申请 -> 模拟支付 -> 管理员审核 -> 投放期内置顶
-- 排序只认 status=ACTIVE + pay_status=PAID + 时间窗口，AI 不参与
-- ============================================================

CREATE TABLE IF NOT EXISTS dish_ad (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    stall_id      BIGINT        NOT NULL COMMENT '档口ID',
    dish_id       BIGINT        NOT NULL COMMENT '菜品ID',
    plan_name     VARCHAR(60)   NULL COMMENT '投放套餐名',
    amount        DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '广告费',
    days          INT           NOT NULL DEFAULT 7 COMMENT '投放天数',
    start_time    DATETIME      NULL COMMENT '开始时间（审核通过时写入）',
    end_time      DATETIME      NULL COMMENT '结束时间（审核通过时写入）',
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACTIVE/REJECTED/EXPIRED/CANCELED',
    pay_status    VARCHAR(20)   NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID/PAID',
    reject_reason VARCHAR(200)  NULL,
    created_by    BIGINT        NULL,
    audited_by    BIGINT        NULL,
    audited_time  DATETIME      NULL,
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_stall_status (stall_id, status),
    KEY idx_dish_window (dish_id, status, start_time, end_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '菜品广告投放';
