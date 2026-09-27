--liquibase formatted sql

--changeset dentalcare:006-create-appointments-core
CREATE TABLE appointments (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    professional_id UUID NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_appointments_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_appointments_professional FOREIGN KEY (professional_id) REFERENCES users (id),
    CONSTRAINT chk_appointments_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_appointments_patient_scheduled_at ON appointments (patient_id, scheduled_at);
CREATE INDEX idx_appointments_professional_scheduled_at ON appointments (professional_id, scheduled_at);
