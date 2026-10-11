--liquibase formatted sql

--changeset dentalcare:044-appointment-contact-attempts-created-at
--validCheckSum: ANY
ALTER TABLE appointment_contact_attempts
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();
