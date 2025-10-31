CREATE TABLE withdrawal_responses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    idempotency_key VARCHAR(32) NOT NULL UNIQUE,
    withdrawal_token VARCHAR(128) NOT NULL,
    response_snapshot JSON NULL,
    error_snapshot JSON NULL,

    PRIMARY KEY (id),
    UNIQUE INDEX idempotency_key_idx (idempotency_key),
    INDEX withdrawal_token_idx (withdrawal_token)
);
