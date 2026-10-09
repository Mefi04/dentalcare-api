--liquibase formatted sql

--changeset dentalcare:040-public-appointment-requests
-- Public requests may not match a registered patient and may not name a preferred dentist.
ALTER TABLE appointment_requests ALTER COLUMN patient_id DROP NOT NULL;
ALTER TABLE appointment_requests ALTER COLUMN requested_professional_id DROP NOT NULL;

ALTER TABLE appointment_requests
    ADD COLUMN requester_full_name VARCHAR(150),
    ADD COLUMN requester_cui VARCHAR(13),
    ADD COLUMN requester_phone VARCHAR(30),
    ADD COLUMN requester_email VARCHAR(255),
    ADD COLUMN request_reason VARCHAR(300),
    ADD COLUMN idempotency_key UUID,
    ADD COLUMN idempotency_payload_hash VARCHAR(64);

ALTER TABLE appointment_requests
    ADD CONSTRAINT chk_appointment_requests_public_contact CHECK (
        requester_full_name IS NULL OR
        (length(trim(requester_full_name)) > 0 AND requester_phone IS NOT NULL
         AND length(trim(requester_phone)) > 0)
    ),
    ADD CONSTRAINT chk_appointment_requests_public_cui CHECK (
        requester_cui IS NULL OR requester_cui ~ '^[0-9]{13}$'
    ),
    ADD CONSTRAINT chk_appointment_requests_idempotency CHECK (
        (idempotency_key IS NULL AND idempotency_payload_hash IS NULL)
        OR (idempotency_key IS NOT NULL AND idempotency_payload_hash ~ '^[0-9a-f]{64}$')
    );

CREATE UNIQUE INDEX uq_appointment_requests_idempotency_key
    ON appointment_requests (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- An active public request for the same CUI, preferred instant, and dentist is unique.
-- A zero UUID normalizes the optional-professional value for PostgreSQL uniqueness.
CREATE UNIQUE INDEX uq_appointment_requests_active_public_equivalent
    ON appointment_requests (
        requester_cui,
        requested_at,
        COALESCE(requested_professional_id, '00000000-0000-0000-0000-000000000000'::UUID)
    )
    WHERE requester_full_name IS NOT NULL
      AND requester_cui IS NOT NULL
      AND status IN ('PENDING', 'PROPOSED');
