-- 敏感词自动屏蔽开关（管理端右上角可切换）
-- 通用 key-value 配置表，供「自动屏蔽」等轻量开关复用。
-- 注意：列名用 cfg_key/cfg_value，避免 key/value 等 MySQL 保留字在 MyBatis-Plus 拼接 SQL 时报语法错。
CREATE TABLE IF NOT EXISTS sys_config (
    cfg_key   VARCHAR(64)  NOT NULL COMMENT '配置键',
    cfg_value VARCHAR(255) NOT NULL DEFAULT '' COMMENT '配置值',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (cfg_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统开关配置';

INSERT INTO sys_config (cfg_key, cfg_value) VALUES ('moderation.auto_block', 'true')
    ON DUPLICATE KEY UPDATE cfg_value = cfg_value;
