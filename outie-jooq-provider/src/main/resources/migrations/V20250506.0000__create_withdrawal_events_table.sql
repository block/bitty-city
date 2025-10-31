CREATE TABLE withdrawal_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    withdrawal_id BIGINT NOT NULL,
    from_state VARCHAR(64) NOT NULL,
    to_state VARCHAR(64) NOT NULL,
    is_completion BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_withdrawal_id (withdrawal_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci; 
