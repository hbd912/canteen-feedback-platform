-- V26: 客服会话「分侧删除」+ 商家可配置的优惠券发放规则。
-- 1) 会话列表太长时，学生/商家各自隐藏自己不想看的会话，互不影响对方的记录。
ALTER TABLE customer_service_session
    ADD COLUMN student_deleted  TINYINT NOT NULL DEFAULT 0 COMMENT '学生侧删除/隐藏 0否 1是' AFTER status,
    ADD COLUMN merchant_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '商家侧删除/隐藏 0否 1是' AFTER student_deleted;

-- 2) 发券规则从硬编码改为商家可配置：折扣区间、是否允许免单、冷却天数、老客门槛、是否允许手动发券。
--    discount_enabled 继续作为「AI 可发券」总开关。
ALTER TABLE merchant_cs_config
    ADD COLUMN manual_grant_enabled TINYINT      NOT NULL DEFAULT 1    COMMENT '商家可手动发券 0关 1开' AFTER discount_enabled,
    ADD COLUMN min_rate            DECIMAL(3,2)  NOT NULL DEFAULT 0.50 COMMENT '最低折扣(0.50=五折)' AFTER manual_grant_enabled,
    ADD COLUMN max_rate            DECIMAL(3,2)  NOT NULL DEFAULT 0.95 COMMENT '最高折扣(0.95=九五折)' AFTER min_rate,
    ADD COLUMN allow_free          TINYINT      NOT NULL DEFAULT 1    COMMENT '允许免单(0折) 0关 1开' AFTER max_rate,
    ADD COLUMN cooldown_days       INT          NOT NULL DEFAULT 30   COMMENT '同一学生同一档口发券冷却天数' AFTER allow_free,
    ADD COLUMN veteran_min_orders  INT          NOT NULL DEFAULT 10   COMMENT '老客门槛：本档口已完成订单数' AFTER cooldown_days;

-- 3) 发券流水原来 uk_stall_user 唯一约束会让「冷却期过后再发」永远插不进去，改为普通索引，
--    冷却改由 cooldown_days 查询判定。
ALTER TABLE customer_service_discount DROP INDEX uk_stall_user;
ALTER TABLE customer_service_discount ADD INDEX idx_stall_user (stall_id, user_id);
