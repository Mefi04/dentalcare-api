--liquibase formatted sql

--changeset dentalcare:034-create-contact-inquiries
CREATE TABLE contact_inquiries (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone VARCHAR(30),
    reason VARCHAR(30) NOT NULL,
    message VARCHAR(4000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    privacy_accepted BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    CONSTRAINT chk_contact_inquiries_name CHECK (BTRIM(name) <> ''),
    CONSTRAINT chk_contact_inquiries_email CHECK (BTRIM(email) <> ''),
    CONSTRAINT chk_contact_inquiries_message CHECK (BTRIM(message) <> ''),
    CONSTRAINT chk_contact_inquiries_reason CHECK (reason IN ('GENERAL', 'SERVICES', 'PRICING', 'OTHER')),
    CONSTRAINT chk_contact_inquiries_status CHECK (status IN ('NEW', 'IN_REVIEW', 'RESOLVED')),
    CONSTRAINT chk_contact_inquiries_privacy CHECK (privacy_accepted = TRUE),
    CONSTRAINT chk_contact_inquiries_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT fk_contact_inquiries_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE RESTRICT
);
CREATE INDEX idx_contact_inquiries_status_created_at ON contact_inquiries (status, created_at DESC, id DESC);

--rollback DROP TABLE contact_inquiries;
