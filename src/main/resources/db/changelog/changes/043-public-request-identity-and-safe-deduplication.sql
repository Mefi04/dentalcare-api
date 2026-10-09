--liquibase formatted sql

--changeset dentalcare:043-public-request-identity-and-safe-deduplication
ALTER TABLE appointment_requests
    ADD COLUMN requester_identity_verified_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN requester_identity_verified_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN requester_identity_verification_method VARCHAR(32),
    ADD CONSTRAINT chk_appointment_request_identity_verification CHECK (
        (requester_identity_verified_at IS NULL
            AND requester_identity_verified_by IS NULL
            AND requester_identity_verification_method IS NULL)
        OR
        (requester_identity_verified_at IS NOT NULL
            AND requester_identity_verified_by IS NOT NULL
            AND requester_identity_verification_method IS NOT NULL
            AND requester_identity_verification_method IN (
                'IN_PERSON', 'CALLBACK_TO_REGISTERED_CONTACT', 'DOCUMENT_REVIEW'
            ))
    );

-- Do not use a user-submitted DPI/CUI as an identity key or reveal matching requests by CUI.
DROP INDEX uq_appointment_requests_active_public_equivalent;
CREATE UNIQUE INDEX uq_appointment_requests_active_public_equivalent
    ON appointment_requests (idempotency_payload_hash)
    WHERE requester_full_name IS NOT NULL
      AND idempotency_payload_hash IS NOT NULL
      AND status IN ('PENDING_CLINIC', 'PENDING_PATIENT');

ALTER TABLE appointment_request_messages
    DROP CONSTRAINT appointment_request_messages_sender_check,
    DROP CONSTRAINT appointment_request_messages_message_type_check;

ALTER TABLE appointment_request_messages
    ADD CONSTRAINT chk_appointment_request_message_sender
        CHECK (sender IN ('CLINIC', 'RECEPTION', 'BOT', 'PATIENT', 'SYSTEM')),
    ADD CONSTRAINT chk_appointment_request_message_type
        CHECK (message_type IN ('PROPOSAL', 'DECISION', 'SCHEDULING_UPDATE'));
