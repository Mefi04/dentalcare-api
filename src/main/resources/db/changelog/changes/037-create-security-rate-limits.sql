--liquibase formatted sql
--changeset danjo:037-create-security-rate-limits
CREATE TABLE security_rate_limits (
    bucket_key VARCHAR(96) PRIMARY KEY,
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_security_rate_limits_count CHECK (request_count > 0),
    CONSTRAINT chk_security_rate_limits_window CHECK (expires_at > window_started_at)
);

CREATE INDEX idx_security_rate_limits_expires_at ON security_rate_limits (expires_at);

--rollback DROP TABLE security_rate_limits;
