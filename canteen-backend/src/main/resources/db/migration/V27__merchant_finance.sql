-- V27: 商家财务。收益按「已支付订单」口径统计（与经营分析/销售情况同一口径），
-- 提现记录单独落表；可提现余额 = 累计收益 - 已提现。
CREATE TABLE merchant_withdrawal (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    stall_id     BIGINT        NOT NULL COMMENT '档口ID',
    amount       DECIMAL(12,2) NOT NULL COMMENT '提现金额',
    status       VARCHAR(20)   NOT NULL DEFAULT 'DONE' COMMENT 'DONE已完成',
    remark       VARCHAR(200)  NULL COMMENT '备注',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提现时间',
    deleted      TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_stall_time (stall_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '商家提现记录';
