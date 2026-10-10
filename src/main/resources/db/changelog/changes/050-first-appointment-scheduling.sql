--liquibase formatted sql

--changeset dentalcare:050-first-appointment-scheduling
-- Existing profiles are deliberately unclassified until an administrator verifies eligibility.
ALTER TABLE professional_public_profiles
    ADD COLUMN service_code VARCHAR(40),
    ADD CONSTRAINT chk_professional_service_code
        CHECK (service_code IS NULL OR service_code IN ('GENERAL_DENTISTRY', 'SPECIALIST'));

CREATE TABLE professional_work_intervals (
    id UUID PRIMARY KEY,
    professional_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    day_of_week INTEGER NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_professional_work_interval CHECK (start_time < end_time)
);
CREATE INDEX idx_professional_work_intervals_lookup
    ON professional_work_intervals (professional_id, day_of_week) WHERE active;

CREATE TABLE professional_schedule_blocks (
    id UUID PRIMARY KEY,
    professional_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reason VARCHAR(150),
    CONSTRAINT chk_professional_schedule_block CHECK (starts_at < ends_at)
);
CREATE INDEX idx_professional_schedule_blocks_lookup
    ON professional_schedule_blocks (professional_id, starts_at, ends_at);

ALTER TABLE appointments
    ADD COLUMN duration_minutes INTEGER NOT NULL DEFAULT 30,
    ADD COLUMN ends_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT chk_appointments_duration CHECK (duration_minutes BETWEEN 5 AND 240);
UPDATE appointments SET ends_at = scheduled_at + INTERVAL '30 minutes';
ALTER TABLE appointments ALTER COLUMN ends_at SET NOT NULL;
ALTER TABLE appointments ADD CONSTRAINT chk_appointments_interval CHECK (ends_at > scheduled_at);

-- Replaces the start-time-only uniqueness rule with interval-level protection.
-- The migration fails safely if existing scheduled appointments overlap.
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE appointments ADD CONSTRAINT ex_appointments_professional_interval
    EXCLUDE USING gist (
        professional_id WITH =,
        tstzrange(scheduled_at, ends_at, '[)') WITH &&
    ) WHERE (status = 'SCHEDULED');

ALTER TABLE appointment_requests
    ADD COLUMN requester_birth_date DATE,
    ADD COLUMN requester_gender VARCHAR(30),
    ADD COLUMN requester_alternative_id VARCHAR(100),
    ADD COLUMN requester_guardian_name VARCHAR(150),
    ADD COLUMN requester_guardian_relationship VARCHAR(100),
    ADD COLUMN requester_guardian_phone VARCHAR(30),
    ADD COLUMN requester_department VARCHAR(100),
    ADD COLUMN requester_municipality VARCHAR(100),
    ADD COLUMN requester_address VARCHAR(255),
    ADD COLUMN requester_emergency_name VARCHAR(150),
    ADD COLUMN requester_emergency_phone VARCHAR(30),
    ADD COLUMN requester_nit VARCHAR(30),
    ADD COLUMN requester_billing_name VARCHAR(150),
    ADD COLUMN requester_billing_address VARCHAR(255),
    ADD COLUMN privacy_notice_version VARCHAR(40),
    ADD COLUMN privacy_accepted_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_appointment_requests_public_queue
    ON appointment_requests (status, requested_at, created_at)
    WHERE requester_full_name IS NOT NULL;
