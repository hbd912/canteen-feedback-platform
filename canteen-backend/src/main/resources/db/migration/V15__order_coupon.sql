-- ============================================================
-- V15 订单优惠券与折扣记录
-- 下单时记录用了哪张券、抵扣多少，取消时用于回滚
-- ============================================================

ALTER TABLE dining_order
    ADD COLUMN coupon_id BIGINT NULL COMMENT '优惠券模板ID' AFTER pay_channel,
    ADD COLUMN user_coupon_id BIGINT NULL COMMENT '用户券ID' AFTER coupon_id,
    ADD COLUMN discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '优惠金额' AFTER total_amount;
