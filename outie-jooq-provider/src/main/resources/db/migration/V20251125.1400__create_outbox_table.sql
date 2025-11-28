CREATE TABLE outbox (
    id VARCHAR(36) NOT NULL,
    value_id VARCHAR(64) NOT NULL,
    effect_type VARCHAR(128) NOT NULL,
    effect_payload JSON NOT NULL,
    created_at BIGINT NOT NULL,
    processed_at BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error TEXT NULL,
    PRIMARY KEY (id),
    INDEX idx_status_created_at (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
