-- ============================================================
-- V17 经验库向量化（RAG）
-- bge-m3 向量存 JSON，检索时在应用层算余弦相似度
-- ============================================================

ALTER TABLE ai_experience
    ADD COLUMN embedding MEDIUMTEXT NULL COMMENT 'bge-m3 向量 JSON',
    ADD COLUMN embedding_model VARCHAR(60) NULL COMMENT '向量模型',
    ADD COLUMN source VARCHAR(20) NULL COMMENT 'MERCHANT_REPLY/FEEDBACK/ADMIN/AI',
    ADD COLUMN ref_id BIGINT NULL COMMENT '来源业务ID（会话/订单等）';
