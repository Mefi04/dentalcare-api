--liquibase formatted sql

--changeset dentalcare:020-create-appointment-requests-waiting-room
CREATE TABLE appointment_requests (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    requested_professional_id UUID NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    proposed_professional_id UUID,
    proposed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(30) NOT NULL,
    processed_by UUID,
    appointment_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_appointment_requests_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_appointment_requests_requested_professional FOREIGN KEY (requested_professional_id) REFERENCES users (id),
    CONSTRAINT fk_appointment_requests_proposed_professional FOREIGN KEY (proposed_professional_id) REFERENCES users (id),
    CONSTRAINT fk_appointment_requests_processed_by FOREIGN KEY (processed_by) REFERENCES users (id),
    CONSTRAINT fk_appointment_requests_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT uq_appointment_requests_appointment UNIQUE (appointment_id),
    CONSTRAINT chk_appointment_requests_status CHECK (status IN ('PENDING', 'PROPOSED', 'CONFIRMED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_appointment_requests_proposal CHECK (
        (status = 'PROPOSED' AND proposed_at IS NOT NULL AND proposed_professional_id IS NOT NULL)
        OR status <> 'PROPOSED'
    ),
    CONSTRAINT chk_appointment_requests_confirmation CHECK (
        (status = 'CONFIRMED' AND appointment_id IS NOT NULL)
        OR (status <> 'CONFIRMED' AND appointment_id IS NULL)
    )
);

CREATE INDEX idx_appointment_requests_patient_created
    ON appointment_requests (patient_id, created_at DESC, id DESC);
CREATE INDEX idx_appointment_requests_status_created
    ON appointment_requests (status, created_at DESC, id DESC);
CREATE INDEX idx_appointment_requests_requested_professional
    ON appointment_requests (requested_professional_id, requested_at);

CREATE TABLE appointment_waiting_room_entries (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    arrived_at TIMESTAMP WITH TIME ZONE NOT NULL,
    waiting_at TIMESTAMP WITH TIME ZONE,
    ready_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    checked_in_by UUID NOT NULL,
    last_updated_by UUID NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_waiting_room_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT fk_waiting_room_checked_in_by FOREIGN KEY (checked_in_by) REFERENCES users (id),
    CONSTRAINT fk_waiting_room_last_updated_by FOREIGN KEY (last_updated_by) REFERENCES users (id),
    CONSTRAINT uq_waiting_room_appointment UNIQUE (appointment_id),
    CONSTRAINT chk_waiting_room_status CHECK (status IN ('ARRIVED', 'WAITING', 'READY', 'CLOSED')),
    CONSTRAINT chk_waiting_room_timestamps CHECK (
        (status = 'ARRIVED' AND waiting_at IS NULL AND ready_at IS NULL AND closed_at IS NULL)
        OR (status = 'WAITING' AND waiting_at IS NOT NULL AND ready_at IS NULL AND closed_at IS NULL)
        OR (status = 'READY' AND waiting_at IS NOT NULL AND ready_at IS NOT NULL AND closed_at IS NULL)
        OR (status = 'CLOSED' AND closed_at IS NOT NULL)
    )
);

CREATE INDEX idx_waiting_room_status_arrived
    ON appointment_waiting_room_entries (status, arrived_at, id);
CREATE INDEX idx_waiting_room_appointment
    ON appointment_waiting_room_entries (appointment_id);
