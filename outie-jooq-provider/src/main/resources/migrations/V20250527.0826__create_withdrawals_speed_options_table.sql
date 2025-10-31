CREATE TABLE withdrawal_speed_options (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    withdrawal_token VARCHAR(128) NOT NULL,
    block_target INT NOT NULL,
    speed VARCHAR(64) NOT NULL,
    total_fee BIGINT NOT NULL,
    total_fee_fiat_units BIGINT NOT NULL,
    total_fee_fiat_currency VARCHAR(16) NOT NULL,
    service_fee BIGINT,
    service_fee_margin INT,
    approximate_wait_time_minutes INT NOT NULL,
    selectable TINYINT NOT NULL,

    PRIMARY KEY (id),
    INDEX withdrawal_idx (withdrawal_token)
);
