--liquibase formatted sql

--changeset dentalcare:046-cleanup-appointments-residual-columns
--validCheckSum: ANY
ALTER TABLE appointments DROP CONSTRAINT IF EXISTS ex_appointments_professional_interval;
ALTER TABLE appointments DROP CONSTRAINT IF EXISTS chk_appointments_interval;
ALTER TABLE appointments DROP CONSTRAINT IF EXISTS chk_appointments_duration;
ALTER TABLE appointments DROP CONSTRAINT IF EXISTS chk_appointments_patient_or_public_contact;
ALTER TABLE appointments DROP COLUMN IF EXISTS ends_at;
ALTER TABLE appointments DROP COLUMN IF EXISTS duration_minutes;

UPDATE appointment_requests
SET status = 'PENDING', proposed_at = NULL, proposed_professional_id = NULL
WHERE id = '6ac28fa6-74f5-49d8-b7a9-955f3725f44d';
