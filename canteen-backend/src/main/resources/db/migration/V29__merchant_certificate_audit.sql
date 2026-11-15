-- 商家资质审核：营业执照 + 健康证 由管理员审核后才能上架菜品
ALTER TABLE canteen_stall
    ADD COLUMN cert_status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
        COMMENT '资质审核状态：PENDING待审核 / APPROVED已通过 / REJECTED已驳回' AFTER license_url,
    ADD COLUMN cert_reject_reason VARCHAR(255) NULL COMMENT '驳回原因' AFTER cert_status,
    ADD COLUMN cert_submit_time  DATETIME     NULL COMMENT '最近提交资质时间' AFTER cert_reject_reason,
    ADD COLUMN cert_review_time  DATETIME     NULL COMMENT '最近审核时间' AFTER cert_submit_time,
    ADD COLUMN cert_reviewed_by  BIGINT       NULL COMMENT '审核管理员ID' AFTER cert_review_time;

-- 存量商家视为已通过，避免升级后所有档口都无法上架；新入驻商家默认 PENDING
UPDATE canteen_stall
SET cert_status = 'APPROVED', cert_review_time = NOW()
WHERE deleted = 0;
