--liquibase formatted sql

--changeset dentalcare:042-public-appointment-conversations
ALTER TABLE appointment_requests DROP CONSTRAINT chk_appointment_requests_status;

UPDATE appointment_requests
SET status = CASE status WHEN 'PENDING' THEN 'PENDING_CLINIC' WHEN 'PROPOSED' THEN 'PENDING_PATIENT' ELSE status END
WHERE requester_full_name IS NOT NULL AND status IN ('PENDING', 'PROPOSED');

ALTER TABLE appointment_requests
    ADD CONSTRAINT chk_appointment_requests_status CHECK (status IN (
        'PENDING', 'PROPOSED', 'PENDING_CLINIC', 'PENDING_PATIENT', 'CONFIRMED', 'REJECTED', 'CANCELLED'
    )),
    ADD COLUMN proposed_expires_at TIMESTAMP WITH TIME ZONE;

DROP INDEX uq_appointment_requests_active_public_equivalent;
CREATE UNIQUE INDEX uq_appointment_requests_active_public_equivalent
    ON appointment_requests (
        requester_cui, requested_at,
        COALESCE(requested_professional_id, '00000000-0000-0000-0000-000000000000'::UUID)
    )
    WHERE requester_full_name IS NOT NULL AND requester_cui IS NOT NULL
      AND status IN ('PENDING_CLINIC', 'PENDING_PATIENT');

CREATE TABLE appointment_public_conversations (
    appointment_request_id UUID PRIMARY KEY REFERENCES appointment_requests(id) ON DELETE CASCADE,
    channel VARCHAR(10) NOT NULL CHECK (channel IN ('SMS', 'EMAIL')),
    verification_code_hash VARCHAR(100),
    verification_expires_at TIMESTAMP WITH TIME ZONE,
    verification_attempts INTEGER NOT NULL DEFAULT 0 CHECK (verification_attempts >= 0),
    conversation_token_hash VARCHAR(64),
    conversation_expires_at TIMESTAMP WITH TIME ZONE,
    decision_idempotency_key UUID,
    decision_idempotency_result VARCHAR(20) CHECK (decision_idempotency_result IN ('ACCEPT', 'REJECT')),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_public_conversation_code_pair CHECK (
        (verification_code_hash IS NULL AND verification_expires_at IS NULL)
        OR (verification_code_hash IS NOT NULL AND verification_expires_at IS NOT NULL)
    ),
    CONSTRAINT chk_public_conversation_token_pair CHECK (
        (conversation_token_hash IS NULL AND conversation_expires_at IS NULL)
        OR (conversation_token_hash IS NOT NULL AND conversation_expires_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_appointment_public_conversation_token_hash
    ON appointment_public_conversations(conversation_token_hash) WHERE conversation_token_hash IS NOT NULL;

CREATE TABLE appointment_request_messages (
    id UUID PRIMARY KEY,
    appointment_request_id UUID NOT NULL REFERENCES appointment_requests(id) ON DELETE CASCADE,
    sender VARCHAR(10) NOT NULL CHECK (sender IN ('CLINIC', 'PATIENT', 'SYSTEM')),
    message_type VARCHAR(30) NOT NULL CHECK (message_type IN ('PROPOSAL', 'DECISION', 'SCHEDULING_UPDATE')),
    message_text VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_appointment_request_messages_history
    ON appointment_request_messages(appointment_request_id, created_at, id);

-- AppointmentService already validates before insert; this constraint closes the concurrent race.
CREATE UNIQUE INDEX uq_appointments_active_professional_time
    ON appointments(professional_id, scheduled_at) WHERE status = 'SCHEDULED';
