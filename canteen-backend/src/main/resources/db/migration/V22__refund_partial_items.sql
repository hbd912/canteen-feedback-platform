-- 部分退款：refund_request 记录退款的菜品项，支持按菜品单独退款
ALTER TABLE refund_request
    ADD COLUMN item_ids VARCHAR(500) NULL COMMENT '退款的订单菜品项ID，逗号分隔；NULL=整单退款',
    ADD COLUMN item_summary VARCHAR(500) NULL COMMENT '退款菜品摘要，如「宫保鸡丁×2、鱼香肉丝×1」';
