--liquibase formatted sql

--changeset dentalcare:005-add-patient-portal-access
-- Patient accounts authenticate with the existing users table. Their email is optional because
-- patient contact email remains in patients.email; staff and bootstrap validation still require it.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

INSERT INTO roles (id, code, name, description, active)
VALUES (gen_random_uuid(), 'PATIENT', 'Paciente', 'Acceso al portal del paciente', TRUE)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE patients ADD COLUMN user_id UUID;
ALTER TABLE patients ADD CONSTRAINT uq_patients_user_id UNIQUE (user_id);
ALTER TABLE patients ADD CONSTRAINT fk_patients_user
    FOREIGN KEY (user_id) REFERENCES users (id);
