-- 余额充值记录：学生给自己账户充值，触发支付网关（mock/alipay），支付成功后增加 sys_user.balance
CREATE TABLE IF NOT EXISTS balance_recharge (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT        NOT NULL,
    amount      DECIMAL(10,2) NOT NULL,
    pay_no      VARCHAR(64),
    channel     VARCHAR(32),
    status      VARCHAR(16)   NOT NULL DEFAULT 'CREATED',
    trade_no    VARCHAR(64),
    create_time DATETIME,
    paid_time   DATETIME,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    KEY idx_user (user_id),
    KEY idx_pay_no (pay_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
