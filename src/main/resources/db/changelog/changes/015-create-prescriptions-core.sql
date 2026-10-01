--liquibase formatted sql

--changeset dentalcare:015-create-prescriptions-core
CREATE TABLE IF NOT EXISTS prescriptions (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    professional_id UUID NOT NULL,
    medication VARCHAR(200) NOT NULL,
    presentation VARCHAR(150) NOT NULL,
    dosage VARCHAR(150) NOT NULL,
    frequency VARCHAR(150) NOT NULL,
    duration VARCHAR(150) NOT NULL,
    instructions VARCHAR(2000),
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL,
    CONSTRAINT fk_prescriptions_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
    CONSTRAINT fk_prescriptions_professional FOREIGN KEY (professional_id) REFERENCES users(id),
    CONSTRAINT chk_prescriptions_medication CHECK (BTRIM(medication) <> ''),
    CONSTRAINT chk_prescriptions_presentation CHECK (BTRIM(presentation) <> ''),
    CONSTRAINT chk_prescriptions_dosage CHECK (BTRIM(dosage) <> ''),
    CONSTRAINT chk_prescriptions_frequency CHECK (BTRIM(frequency) <> ''),
    CONSTRAINT chk_prescriptions_duration CHECK (BTRIM(duration) <> ''),
    CONSTRAINT chk_prescriptions_status CHECK (status IN ('ISSUED'))
);

CREATE INDEX IF NOT EXISTS idx_prescriptions_patient_issued ON prescriptions(patient_id, issued_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_prescriptions_professional ON prescriptions(professional_id);

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'PRESCRIPTION_READ', 'Read patient prescriptions'),
    (gen_random_uuid(), 'PRESCRIPTION_CREATE', 'Issue patient prescriptions')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE (p.code='PRESCRIPTION_READ' AND r.code IN ('ADMINISTRATOR','DENTIST','ASSISTANT'))
   OR (p.code='PRESCRIPTION_CREATE' AND r.code='DENTIST')
ON CONFLICT (role_id, permission_id) DO NOTHING;
