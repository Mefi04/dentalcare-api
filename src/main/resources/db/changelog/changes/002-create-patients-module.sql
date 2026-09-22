--liquibase formatted sql

--changeset dentalcare:002-create-patients-module
CREATE SEQUENCE patient_code_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE patients (
    id UUID PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(150) NOT NULL,
    dpi VARCHAR(13) NOT NULL,
    birth_date DATE NOT NULL,
    gender VARCHAR(10) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    email VARCHAR(255),
    city VARCHAR(100),
    address VARCHAR(255),
    emergency_contact VARCHAR(150),
    emergency_phone VARCHAR(30),
    billing_name VARCHAR(150),
    nit VARCHAR(30),
    billing_address VARCHAR(255),
    guardian_name VARCHAR(150),
    guardian_relationship VARCHAR(100),
    guardian_phone VARCHAR(30),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_patients_code UNIQUE (code),
    CONSTRAINT uq_patients_dpi UNIQUE (dpi),
    CONSTRAINT chk_patients_gender CHECK (gender IN ('FEMALE', 'MALE', 'OTHER'))
);

CREATE INDEX idx_patients_phone ON patients (phone);
CREATE INDEX idx_patients_email ON patients (email);
CREATE INDEX idx_patients_city ON patients (city);

INSERT INTO roles (id, code, name, description, active) VALUES
    (gen_random_uuid(), 'ADMINISTRATOR', 'Administrator', 'System administrator', TRUE),
    (gen_random_uuid(), 'SECRETARY', 'Secretary', 'Administrative secretary', TRUE),
    (gen_random_uuid(), 'DENTIST', 'Dentist', 'Dental professional', TRUE),
    (gen_random_uuid(), 'ASSISTANT', 'Assistant', 'Dental assistant', TRUE),
    (gen_random_uuid(), 'CASHIER', 'Cashier', 'Billing cashier', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'PATIENT_READ', 'Read patient administrative information'),
    (gen_random_uuid(), 'PATIENT_CREATE', 'Create patients'),
    (gen_random_uuid(), 'PATIENT_UPDATE', 'Update patient administrative information')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE (p.code = 'PATIENT_READ' AND r.code IN ('ADMINISTRATOR', 'SECRETARY', 'DENTIST', 'ASSISTANT', 'CASHIER'))
   OR (p.code IN ('PATIENT_CREATE', 'PATIENT_UPDATE') AND r.code IN ('ADMINISTRATOR', 'SECRETARY'))
ON CONFLICT (role_id, permission_id) DO NOTHING;
