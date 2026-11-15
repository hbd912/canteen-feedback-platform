-- ============================================================
-- V19 经验命中文档（超级后台日志用）
-- 记录每次 RAG 检索命中了哪条经验、相似度多少，便于追溯与调阈值
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_experience_hit (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    scene         VARCHAR(60)  NULL,
    query_text    VARCHAR(500) NULL,
    experience_id BIGINT       NULL,
    score         DECIMAL(6,4) NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_create (create_time),
    KEY idx_experience (experience_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '经验库检索命中记录';
