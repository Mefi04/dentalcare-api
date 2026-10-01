--liquibase formatted sql

--changeset dentalcare:018-create-password-recovery-tokens
CREATE TABLE password_recovery_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_password_recovery_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_password_recovery_expiration CHECK (expires_at > requested_at),
    CONSTRAINT chk_password_recovery_attempts CHECK (failed_attempts >= 0),
    CONSTRAINT chk_password_recovery_terminal_state CHECK (used_at IS NULL OR revoked_at IS NULL)
);

CREATE INDEX idx_password_recovery_tokens_user_requested
    ON password_recovery_tokens (user_id, requested_at DESC);
CREATE INDEX idx_password_recovery_tokens_expiration
    ON password_recovery_tokens (expires_at)
    WHERE used_at IS NULL AND revoked_at IS NULL;
