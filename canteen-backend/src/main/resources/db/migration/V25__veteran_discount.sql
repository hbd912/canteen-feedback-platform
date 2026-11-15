-- V25: 老顾客安抚折扣。AI 生成带「给券：」标记时，系统发一张该档口的真实优惠券给该学生，
-- 并加持冷却（同一学生同一档口 30 天内最多发 1 张），杜绝「AI 空头承诺→不兑现」的矛盾。
ALTER TABLE merchant_cs_config
    ADD COLUMN discount_enabled TINYINT NOT NULL DEFAULT 1 COMMENT 'AI 老顾客安抚发券开关 0关 1开' AFTER greeting;

-- 发券流水：记录谁在哪个会话因为哪一档(新客/常客/老客)拿到了哪张券，用于冷却判断与商家统计
CREATE TABLE customer_service_discount (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    stall_id     BIGINT       NOT NULL COMMENT '档口ID',
    user_id      BIGINT       NOT NULL COMMENT '学生ID',
    session_id   BIGINT       NULL COMMENT '会话ID',
    coupon_id    BIGINT       NOT NULL COMMENT '发放的券模板ID',
    user_coupon_id BIGINT     NOT NULL COMMENT '学生持有的券ID',
    tier         VARCHAR(20)  NULL COMMENT '分层 新客/常客/老客',
    discount_value DECIMAL(4,2) NULL COMMENT '折扣值(0.8=八折)',
    grant_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发放时间',
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stall_user (stall_id, user_id),
    KEY idx_user_time (user_id, grant_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '客服AI老顾客安抚发券流水';