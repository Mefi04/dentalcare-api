--liquibase formatted sql

--changeset dentalcare:035-update-contact-inquiries-reason-constraint
ALTER TABLE contact_inquiries DROP CONSTRAINT IF EXISTS chk_contact_inquiries_reason;

UPDATE contact_inquiries SET reason = 'OTHER' WHERE reason = 'PRICING';

ALTER TABLE contact_inquiries ADD CONSTRAINT chk_contact_inquiries_reason
    CHECK (reason IN ('GENERAL', 'SERVICES', 'PROFESSIONALS', 'LOCATIONS', 'APPOINTMENT_HELP', 'ACCOUNT_ACTIVATION', 'OTHER'));

--rollback ALTER TABLE contact_inquiries DROP CONSTRAINT IF EXISTS chk_contact_inquiries_reason;
--rollback ALTER TABLE contact_inquiries ADD CONSTRAINT chk_contact_inquiries_reason CHECK (reason IN ('GENERAL', 'SERVICES', 'PRICING', 'OTHER'));
