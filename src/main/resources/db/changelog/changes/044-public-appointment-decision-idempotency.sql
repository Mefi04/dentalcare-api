--liquibase formatted sql

--changeset dentalcare:044-public-appointment-decision-idempotency
CREATE TABLE appointment_public_decisions (
    id UUID PRIMARY KEY,
    appointment_request_id UUID NOT NULL REFERENCES appointment_requests(id) ON DELETE CASCADE,
    idempotency_key UUID NOT NULL,
    decision VARCHAR(10) NOT NULL CHECK (decision IN ('ACCEPT', 'REJECT')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_appointment_public_decision_key UNIQUE (appointment_request_id, idempotency_key)
);

INSERT INTO appointment_public_decisions (id, appointment_request_id, idempotency_key, decision, created_at)
SELECT gen_random_uuid(), appointment_request_id, decision_idempotency_key,
       decision_idempotency_result, updated_at
FROM appointment_public_conversations
WHERE decision_idempotency_key IS NOT NULL AND decision_idempotency_result IS NOT NULL;

ALTER TABLE appointment_public_conversations
    DROP COLUMN decision_idempotency_key,
    DROP COLUMN decision_idempotency_result;
