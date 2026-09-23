--liquibase formatted sql

--changeset dentalcare:004-add-staff-user-management
ALTER TABLE users ADD COLUMN full_name VARCHAR(150);

UPDATE users
SET full_name = username
WHERE full_name IS NULL;

ALTER TABLE users ALTER COLUMN full_name SET NOT NULL;

INSERT INTO roles (id, code, name, description, active) VALUES
    (gen_random_uuid(), 'ADMINISTRATOR', 'Administrador', 'Administrador del sistema', TRUE),
    (gen_random_uuid(), 'SECRETARY', 'Secretaría', 'Personal de secretaría', TRUE),
    (gen_random_uuid(), 'DENTIST', 'Odontólogo', 'Profesional odontológico', TRUE),
    (gen_random_uuid(), 'ASSISTANT', 'Asistente', 'Asistente dental', TRUE),
    (gen_random_uuid(), 'CASHIER', 'Cajero', 'Personal de caja', TRUE)
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;
