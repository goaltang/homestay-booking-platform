CREATE TABLE auth_sessions (
    id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    credential_digest VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_auth_sessions_expiry (expires_at),
    INDEX idx_auth_sessions_user (user_id),
    CONSTRAINT fk_auth_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
