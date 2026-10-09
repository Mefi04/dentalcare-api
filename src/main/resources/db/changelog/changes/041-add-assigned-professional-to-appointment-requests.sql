--liquibase formatted sql

--changeset dentalcare:041-add-assigned-professional-to-appointment-requests
ALTER TABLE appointment_requests
    ADD COLUMN assigned_professional_id UUID;

ALTER TABLE appointment_requests
    ADD CONSTRAINT fk_appointment_requests_assigned_professional
        FOREIGN KEY (assigned_professional_id) REFERENCES users (id);

CREATE INDEX idx_appointment_requests_assigned_professional
    ON appointment_requests (assigned_professional_id);
