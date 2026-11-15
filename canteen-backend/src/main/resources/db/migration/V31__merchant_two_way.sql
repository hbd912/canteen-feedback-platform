-- 商家留言升级为「管理端↔商家」双向站内信线程：
-- 同一张表同时存 商家→管理员(IN) 与 管理员→商家(OUT)，按档口组织历史记录。
ALTER TABLE merchant_message
    ADD COLUMN direction   VARCHAR(8)        NOT NULL DEFAULT 'IN'   COMMENT '方向：IN 商家→管理员 / OUT 管理员→商家' AFTER stall_id,
    ADD COLUMN author_role VARCHAR(16)       NOT NULL DEFAULT 'MERCHANT' COMMENT '发送方角色：MERCHANT / ADMIN / AI' AFTER direction,
    ADD COLUMN notice      TINYINT           NOT NULL DEFAULT 0      COMMENT '1=置顶通知抬头（AI/系统自动生成，列表置顶高亮）' AFTER author_role,
    ADD COLUMN title       VARCHAR(100)      NULL COMMENT '通知/反馈标题（可空）' AFTER notice,
    MODIFY COLUMN user_id  BIGINT            NULL COMMENT '发送方用户ID（管理员消息可空）';

-- 存量留言方向归为 商家→管理员
UPDATE merchant_message
SET direction = 'IN', author_role = 'MERCHANT'
WHERE direction = 'IN';

-- 通知也要进商家铃铛：新通知类型在后续用到时才需要，这里仅扩展索引以支撑按档口拉线程
ALTER TABLE merchant_message
    ADD KEY idx_stall_dir (stall_id, direction, create_time);