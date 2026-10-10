--liquibase formatted sql

--changeset dentalcare:049-appointment-contact-attempts
CREATE TABLE appointment_contact_attempts (
    id UUID PRIMARY KEY,
    appointment_request_id UUID NOT NULL REFERENCES appointment_requests(id) ON DELETE RESTRICT,
    actor_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    result VARCHAR(30) NOT NULL,
    observation VARCHAR(500),
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_appointment_contact_result CHECK
        (result IN ('CONTACTED', 'NO_ANSWER', 'CALL_BACK_LATER', 'WRONG_NUMBER', 'DECLINED')),
    CONSTRAINT chk_appointment_contact_next CHECK
        (next_attempt_at IS NULL OR next_attempt_at > attempted_at)
);

CREATE INDEX idx_appointment_contact_request_time
    ON appointment_contact_attempts (appointment_request_id, attempted_at DESC, id DESC);
