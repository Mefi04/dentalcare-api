--liquibase formatted sql

--changeset dentalcare:045-appointment-chat-messages-and-notification-outbox
ALTER TABLE appointment_request_messages
    ADD COLUMN idempotency_key UUID,
    ADD COLUMN idempotency_payload_hash VARCHAR(64);

ALTER TABLE appointment_request_messages
    DROP CONSTRAINT chk_appointment_request_message_type,
    ADD CONSTRAINT chk_appointment_request_message_type
        CHECK (message_type IN ('PROPOSAL', 'DECISION', 'SCHEDULING_UPDATE', 'FREE_TEXT'));

CREATE UNIQUE INDEX uq_appointment_message_sender_idempotency
    ON appointment_request_messages(appointment_request_id, sender, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE appointment_notification_outbox (
    id UUID PRIMARY KEY,
    appointment_request_id UUID NOT NULL REFERENCES appointment_requests(id) ON DELETE CASCADE,
    event_type VARCHAR(20) NOT NULL CHECK (event_type IN ('OTP', 'NOTICE')),
    channel VARCHAR(10) NOT NULL CHECK (channel IN ('SMS', 'EMAIL')),
    recipient_ciphertext TEXT,
    payload_ciphertext TEXT,
    status VARCHAR(20) NOT NULL CHECK (status IN (
        'PENDING', 'PROCESSING', 'SENT', 'FAILED', 'RETRY_PENDING', 'DEAD_LETTER'
    )),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    processing_started_at TIMESTAMP WITH TIME ZONE,
    sent_at TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(50),
    correlation_id UUID NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    CONSTRAINT chk_appointment_outbox_processing_lease CHECK (
        (status = 'PROCESSING' AND processing_started_at IS NOT NULL)
        OR (status <> 'PROCESSING' AND processing_started_at IS NULL)
    ),
    CONSTRAINT chk_appointment_outbox_sent_timestamp CHECK (
        (status = 'SENT' AND sent_at IS NOT NULL) OR (status <> 'SENT' AND sent_at IS NULL)
    ),
    CONSTRAINT chk_appointment_outbox_sensitive_payload CHECK (
        ((status IN ('PENDING', 'PROCESSING', 'RETRY_PENDING')) AND recipient_ciphertext IS NOT NULL AND payload_ciphertext IS NOT NULL)
        OR ((status IN ('SENT', 'FAILED', 'DEAD_LETTER')) AND recipient_ciphertext IS NULL AND payload_ciphertext IS NULL)
    )
);

CREATE INDEX idx_appointment_outbox_due
    ON appointment_notification_outbox(status, next_attempt_at, created_at, id);
CREATE INDEX idx_appointment_outbox_request
    ON appointment_notification_outbox(appointment_request_id, created_at DESC, id DESC);

--rollback DROP TABLE appointment_notification_outbox;
--rollback DROP INDEX uq_appointment_message_sender_idempotency;
--rollback ALTER TABLE appointment_request_messages DROP COLUMN idempotency_payload_hash, DROP COLUMN idempotency_key;
--rollback ALTER TABLE appointment_request_messages DROP CONSTRAINT chk_appointment_request_message_type;
--rollback ALTER TABLE appointment_request_messages ADD CONSTRAINT chk_appointment_request_message_type CHECK (message_type IN ('PROPOSAL', 'DECISION', 'SCHEDULING_UPDATE'));
