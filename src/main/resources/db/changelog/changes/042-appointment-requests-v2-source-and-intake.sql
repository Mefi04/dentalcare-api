--liquibase formatted sql

--changeset dentalcare:042-appointment-requests-v2-source-and-intake
--validCheckSum: ANY
--validCheckSum: 9:4f385d3101f2cce04a6090b82c1d79b2
-- 1. Source classification on appointment requests
ALTER TABLE appointment_requests
    ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'PATIENT_PORTAL';

-- Historic classification: requests with requester_full_name came from public
UPDATE appointment_requests
SET source = 'PUBLIC'
WHERE requester_full_name IS NOT NULL AND source = 'PATIENT_PORTAL';

ALTER TABLE appointment_requests
    DROP CONSTRAINT IF EXISTS chk_appointment_requests_source;

ALTER TABLE appointment_requests
    ADD CONSTRAINT chk_appointment_requests_source CHECK (source IN ('PUBLIC', 'PATIENT_PORTAL'));

-- 2. Extended intake fields for public visitors
ALTER TABLE appointment_requests
    ADD COLUMN IF NOT EXISTS requester_birth_date DATE,
    ADD COLUMN IF NOT EXISTS requester_gender VARCHAR(20),
    ADD COLUMN IF NOT EXISTS requester_alternative_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS requester_guardian_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS requester_guardian_relationship VARCHAR(100),
    ADD COLUMN IF NOT EXISTS requester_guardian_phone VARCHAR(30),
    ADD COLUMN IF NOT EXISTS requester_department VARCHAR(100),
    ADD COLUMN IF NOT EXISTS requester_municipality VARCHAR(100),
    ADD COLUMN IF NOT EXISTS requester_address VARCHAR(255),
    ADD COLUMN IF NOT EXISTS requester_emergency_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS requester_emergency_phone VARCHAR(30),
    ADD COLUMN IF NOT EXISTS requester_nit VARCHAR(30),
    ADD COLUMN IF NOT EXISTS requester_billing_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS requester_billing_address VARCHAR(255),
    ADD COLUMN IF NOT EXISTS privacy_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS privacy_notice_version VARCHAR(40),
    ADD COLUMN IF NOT EXISTS privacy_accepted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE appointment_requests
    DROP CONSTRAINT IF EXISTS chk_appointment_requests_gender;

ALTER TABLE appointment_requests
    ADD CONSTRAINT chk_appointment_requests_gender CHECK (requester_gender IS NULL OR requester_gender IN ('MALE', 'FEMALE', 'OTHER'));

CREATE INDEX IF NOT EXISTS idx_appointment_requests_source_status_created
    ON appointment_requests (source, status, created_at DESC, id DESC);

-- 3. Allow appointments to exist for confirmed public requests without requiring an existing patient record
ALTER TABLE appointments ALTER COLUMN patient_id DROP NOT NULL;

ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS public_contact_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS public_contact_phone VARCHAR(30);

ALTER TABLE appointments
    DROP CONSTRAINT IF EXISTS chk_appointments_subject;

ALTER TABLE appointments
    ADD CONSTRAINT chk_appointments_subject CHECK (
        (patient_id IS NOT NULL) OR
        (patient_id IS NULL AND public_contact_name IS NOT NULL AND length(trim(public_contact_name)) > 0
         AND public_contact_phone IS NOT NULL AND length(trim(public_contact_phone)) > 0)
    );

--rollback ALTER TABLE appointments DROP CONSTRAINT IF EXISTS chk_appointments_subject;
--rollback ALTER TABLE appointments DROP COLUMN IF EXISTS public_contact_phone;
--rollback ALTER TABLE appointments DROP COLUMN IF EXISTS public_contact_name;
--rollback DROP INDEX IF EXISTS idx_appointment_requests_source_status_created;
--rollback ALTER TABLE appointment_requests DROP CONSTRAINT IF EXISTS chk_appointment_requests_gender;
--rollback ALTER TABLE appointment_requests DROP CONSTRAINT IF EXISTS chk_appointment_requests_source;
--rollback ALTER TABLE appointment_requests DROP COLUMN IF EXISTS source;
