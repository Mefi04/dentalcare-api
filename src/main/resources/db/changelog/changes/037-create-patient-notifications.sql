--liquibase formatted sql

--changeset dentalcare:037-create-patient-notifications
CREATE TABLE patient_notifications (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_patient_notifications_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE,
    CONSTRAINT chk_patient_notifications_event_type CHECK (event_type IN (
        'APPOINTMENT_SCHEDULED', 'APPOINTMENT_RESCHEDULED', 'APPOINTMENT_CANCELLED',
        'PAYMENT_REGISTERED', 'PRESCRIPTION_ISSUED', 'CLINIC_INFORMATION_UPDATED'
    )),
    CONSTRAINT chk_patient_notifications_title CHECK (BTRIM(title) <> ''),
    CONSTRAINT chk_patient_notifications_message CHECK (BTRIM(message) <> ''),
    CONSTRAINT chk_patient_notifications_read_at CHECK (read_at IS NULL OR read_at >= created_at)
);

CREATE INDEX idx_patient_notifications_patient_created
    ON patient_notifications (patient_id, created_at DESC, id DESC);
CREATE INDEX idx_patient_notifications_patient_unread
    ON patient_notifications (patient_id, read_at, created_at DESC);

CREATE TABLE patient_notification_preferences (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL UNIQUE,
    appointments_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    payments_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    medications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    clinic_updates_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_patient_notification_preferences_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE,
    CONSTRAINT chk_patient_notification_preferences_timestamps CHECK (updated_at >= created_at)
);

--rollback DROP TABLE patient_notification_preferences;
--rollback DROP TABLE patient_notifications;
