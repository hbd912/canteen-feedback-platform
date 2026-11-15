-- 消息通知：增加通用跳转字段，支持按角色推送各类业务事件（订单/退款/出餐等）
ALTER TABLE notification
    ADD COLUMN target_url VARCHAR(200) NULL COMMENT '点击跳转的前端路由，如 /merchant/orders',
    ADD COLUMN target_id BIGINT NULL COMMENT '跳转后定位高亮的业务对象ID';
