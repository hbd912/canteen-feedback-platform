-- 账户流水：记录余额的每一笔变动（充值 / 下单消费 / 退款回补），用于「支付明细」展示
CREATE TABLE IF NOT EXISTS account_transaction (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT        NOT NULL,
    type        VARCHAR(16)   NOT NULL,
    amount      DECIMAL(10,2) NOT NULL,
    balance     DECIMAL(10,2),
    ref_no      VARCHAR(64),
    remark      VARCHAR(200),
    create_time DATETIME,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    KEY idx_user_time (user_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
