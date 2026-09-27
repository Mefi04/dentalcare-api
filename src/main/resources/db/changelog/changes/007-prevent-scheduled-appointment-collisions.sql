--liquibase formatted sql

--changeset dentalcare:007-prevent-scheduled-appointment-collisions
CREATE UNIQUE INDEX uq_appointments_professional_scheduled
    ON appointments (professional_id, scheduled_at)
    WHERE status = 'SCHEDULED';
