--liquibase formatted sql

--changeset dentalcare:043-professional-service-code-and-calls
--validCheckSum: ANY
-- 1. Service classification for professional profiles (to strictly filter General Dentistry)
ALTER TABLE professional_public_profiles
    ADD COLUMN IF NOT EXISTS service_code VARCHAR(50) NOT NULL DEFAULT 'GENERAL_DENTISTRY';

UPDATE professional_public_profiles
    SET service_code = 'GENERAL_DENTISTRY'
    WHERE service_code IS NULL;

ALTER TABLE professional_public_profiles
    DROP CONSTRAINT IF EXISTS chk_professional_public_profiles_service_code;

ALTER TABLE professional_public_profiles
    ADD CONSTRAINT chk_professional_public_profiles_service_code
        CHECK (service_code IN ('GENERAL_DENTISTRY', 'ORTHODONTICS', 'ENDODONTICS', 'PERIODONTICS', 'PEDIATRIC_DENTISTRY', 'ORAL_SURGERY'));

CREATE INDEX IF NOT EXISTS idx_professional_public_profiles_service_code_visible
    ON professional_public_profiles (service_code, public_visible);

-- 2. Contact attempts table for receptionist call logging on public requests
CREATE TABLE IF NOT EXISTS appointment_contact_attempts (
    id UUID PRIMARY KEY,
    appointment_request_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    result VARCHAR(30) NOT NULL,
    observation VARCHAR(500),
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_appointment_contact_attempts_request FOREIGN KEY (appointment_request_id) REFERENCES appointment_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_appointment_contact_attempts_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    CONSTRAINT chk_appointment_contact_attempts_result CHECK (result IN ('CONTACTED', 'NO_ANSWER', 'CALL_BACK_LATER', 'WRONG_NUMBER', 'DECLINED')),
    CONSTRAINT chk_appointment_contact_attempts_next CHECK (next_attempt_at IS NULL OR next_attempt_at > attempted_at)
);

CREATE INDEX IF NOT EXISTS idx_appointment_contact_attempts_request_attempted
    ON appointment_contact_attempts (appointment_request_id, attempted_at DESC);

--rollback DROP TABLE IF EXISTS appointment_contact_attempts;
--rollback DROP INDEX IF EXISTS idx_professional_public_profiles_service_code_visible;
--rollback ALTER TABLE professional_public_profiles DROP CONSTRAINT IF EXISTS chk_professional_public_profiles_service_code;
--rollback ALTER TABLE professional_public_profiles DROP COLUMN IF EXISTS service_code;
