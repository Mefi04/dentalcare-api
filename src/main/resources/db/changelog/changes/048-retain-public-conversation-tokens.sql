--liquibase formatted sql

--changeset dentalcare:048-retain-public-conversation-tokens
CREATE TABLE appointment_public_conversation_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    appointment_request_id UUID NOT NULL REFERENCES appointment_public_conversations(appointment_request_id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_public_conversation_tokens_request_expiry
    ON appointment_public_conversation_tokens(appointment_request_id, expires_at);

--rollback DROP TABLE IF EXISTS appointment_public_conversation_tokens;
