-- 菜品上架审核：商家新建/重新上架需管理员审核，通过后才出现在学生端
ALTER TABLE dish
    ADD COLUMN audit_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED'
        COMMENT '审核状态：PENDING待审核 / APPROVED已通过 / REJECTED已驳回' AFTER status,
    ADD COLUMN audit_remark VARCHAR(300) NULL COMMENT '审核备注（驳回原因）' AFTER audit_status,
    ADD COLUMN audit_by     BIGINT       NULL COMMENT '审核管理员ID' AFTER audit_remark,
    ADD COLUMN audit_time   DATETIME     NULL COMMENT '最近审核时间' AFTER audit_by;

-- 存量菜品视为已通过，升级后不影响学生端已有菜单
UPDATE dish
SET audit_status = 'APPROVED'
WHERE deleted = 0;
