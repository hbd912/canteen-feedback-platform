-- V28: 优惠券适用范围（整单 / 单份菜）+ 客服发券改为「可领取卡片」。
-- 1) 券模板增加适用范围与限定菜品；private_gift 标记客服发券专用，不在领券中心展示。
ALTER TABLE coupon
    ADD COLUMN scope        VARCHAR(20) NOT NULL DEFAULT 'ORDER' COMMENT 'ORDER=整单 / ITEM=单份菜' AFTER type,
    ADD COLUMN dish_id      BIGINT      NULL COMMENT 'ITEM 券限定的菜品ID；为空表示任意单份菜' AFTER scope,
    ADD COLUMN private_gift TINYINT     NOT NULL DEFAULT 0 COMMENT '客服发券专用 1是 0否' AFTER status;

-- 2) 客服发券 offer：商家发出后学生需点击「领取」才真正到账。
CREATE TABLE coupon_offer (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    stall_id    BIGINT      NOT NULL COMMENT '档口ID',
    user_id     BIGINT      NOT NULL COMMENT '指定学生ID',
    coupon_id   BIGINT      NOT NULL COMMENT '券模板ID',
    session_id  BIGINT      NULL COMMENT '客服会话ID',
    status      VARCHAR(20) NOT NULL DEFAULT 'UNCLAIMED' COMMENT 'UNCLAIMED待领取/CLAIMED已领取',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claim_time  DATETIME    NULL,
    deleted     TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user_status (user_id, status),
    KEY idx_coupon (coupon_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '客服发券可领取凭证';

-- 3) 客服消息挂上 offer，前端渲染成券卡片。
ALTER TABLE customer_service_message
    ADD COLUMN coupon_offer_id BIGINT NULL COMMENT '客服发券卡片对应的 offer' AFTER dish_id;

-- 4) 发券流水允许「已发送但未领取」：首次领取前 user_coupon_id 为空。
ALTER TABLE customer_service_discount MODIFY COLUMN user_coupon_id BIGINT NULL COMMENT '学生持有的券ID（未领取时为空）';
