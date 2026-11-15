-- ============================================================
-- V12 客服菜品咨询 + 评价 AI 托管
-- 1) 客服消息可关联一道真实菜品，学生与商家看到同一张菜品卡片
-- 2) 商家可开启评价 AI 托管；回复来源区分手动与 AI
-- 3) 合并意外产生的重复会话，并保证一个学生对一个档口仅一条会话
-- ============================================================

ALTER TABLE customer_service_message
    ADD COLUMN dish_id BIGINT NULL COMMENT '咨询菜品ID' AFTER content;

ALTER TABLE merchant_cs_config
    ADD COLUMN review_ai_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '评价AI托管 0关 1开' AFTER ai_enabled;

ALTER TABLE dish_review
    ADD COLUMN merchant_reply_source VARCHAR(10) NULL COMMENT 'MANUAL人工 AI智能' AFTER merchant_reply_time;

-- 极端情况下历史并发可能产生重复会话：先把消息归并到最早会话，再删除空壳。
UPDATE customer_service_message m
JOIN customer_service_session duplicate_session ON m.session_id = duplicate_session.id
JOIN customer_service_session keep_session
  ON keep_session.stall_id = duplicate_session.stall_id
 AND keep_session.user_id = duplicate_session.user_id
 AND keep_session.id < duplicate_session.id
SET m.session_id = keep_session.id;

DELETE duplicate_session
FROM customer_service_session duplicate_session
JOIN customer_service_session keep_session
  ON keep_session.stall_id = duplicate_session.stall_id
 AND keep_session.user_id = duplicate_session.user_id
 AND keep_session.id < duplicate_session.id;

ALTER TABLE customer_service_session
    ADD UNIQUE KEY uk_cs_stall_user (stall_id, user_id);
