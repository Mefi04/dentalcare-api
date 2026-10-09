--liquibase formatted sql

--changeset dentalcare:047-confirm-public-appointments-before-patient-registration
ALTER TABLE appointments ALTER COLUMN patient_id DROP NOT NULL;
ALTER TABLE appointments
    ADD COLUMN public_contact_name VARCHAR(150),
    ADD COLUMN public_contact_phone VARCHAR(30),
    ADD CONSTRAINT chk_appointments_patient_or_public_contact CHECK (
        patient_id IS NOT NULL OR
        (public_contact_name IS NOT NULL AND length(trim(public_contact_name)) > 0
         AND public_contact_phone IS NOT NULL AND length(trim(public_contact_phone)) > 0)
    );

--rollback ALTER TABLE appointments DROP CONSTRAINT IF EXISTS chk_appointments_patient_or_public_contact;
--rollback ALTER TABLE appointments DROP COLUMN IF EXISTS public_contact_phone;
--rollback ALTER TABLE appointments DROP COLUMN IF EXISTS public_contact_name;
--rollback ALTER TABLE appointments ALTER COLUMN patient_id SET NOT NULL;
