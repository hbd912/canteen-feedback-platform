-- ============================================================
-- V18 广告效果统计 + 退款链路字段
-- 曝光/点击/转化按「广告 + 日期」聚合，转化归因在订单项上记录 ad_id
-- ============================================================

CREATE TABLE IF NOT EXISTS dish_ad_stat (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    ad_id       BIGINT   NOT NULL,
    stat_date   DATE     NOT NULL,
    impressions BIGINT   NOT NULL DEFAULT 0,
    clicks      BIGINT   NOT NULL DEFAULT 0,
    conversions BIGINT   NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ad_date (ad_id, stat_date),
    KEY idx_date (stat_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '广告每日效果';

ALTER TABLE dining_order_item
    ADD COLUMN ad_id BIGINT NULL COMMENT '归因广告ID（下单时命中的投放）' AFTER dish_id;

ALTER TABLE dining_order
    ADD COLUMN refund_status VARCHAR(20) NULL COMMENT 'NONE/PENDING/REFUNDED/REJECTED/FAILED' AFTER pay_status;

ALTER TABLE refund_request
    ADD COLUMN refund_no VARCHAR(64) NULL COMMENT '退款单号' AFTER status,
    ADD COLUMN fail_reason VARCHAR(300) NULL COMMENT '退款失败原因' AFTER remark;
