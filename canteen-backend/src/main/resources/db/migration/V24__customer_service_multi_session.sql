-- V24: 客服会话支持多会话（同一学生对同一档口可建多个会话/切换）。
-- 原 V12 的 uk_cs_stall_user 唯一约束强制「一档口一学生一会话」，放开为普通索引，
-- 用 status=OPEN/CLOSED 区分当前活跃会话与已归档会话（复用已有字段，无新增列）。
ALTER TABLE customer_service_session DROP INDEX uk_cs_stall_user;
ALTER TABLE customer_service_session ADD INDEX idx_stall_user_open (stall_id, user_id);