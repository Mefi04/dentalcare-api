--liquibase formatted sql

--changeset dentalcare:008-create-medical-history-core
CREATE TABLE medical_histories (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    observations VARCHAR(4000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_medical_histories_patient UNIQUE (patient_id),
    CONSTRAINT fk_medical_histories_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE
);

CREATE TABLE medical_history_allergies (
    medical_history_id UUID NOT NULL,
    description VARCHAR(200) NOT NULL,
    CONSTRAINT pk_medical_history_allergies PRIMARY KEY (medical_history_id, description),
    CONSTRAINT fk_medical_history_allergies_history
        FOREIGN KEY (medical_history_id) REFERENCES medical_histories (id) ON DELETE CASCADE,
    CONSTRAINT chk_medical_history_allergies_description
        CHECK (BTRIM(description) <> '')
);

CREATE TABLE medical_history_medications (
    medical_history_id UUID NOT NULL,
    description VARCHAR(200) NOT NULL,
    CONSTRAINT pk_medical_history_medications PRIMARY KEY (medical_history_id, description),
    CONSTRAINT fk_medical_history_medications_history
        FOREIGN KEY (medical_history_id) REFERENCES medical_histories (id) ON DELETE CASCADE,
    CONSTRAINT chk_medical_history_medications_description
        CHECK (BTRIM(description) <> '')
);

CREATE TABLE medical_history_conditions (
    medical_history_id UUID NOT NULL,
    description VARCHAR(200) NOT NULL,
    CONSTRAINT pk_medical_history_conditions PRIMARY KEY (medical_history_id, description),
    CONSTRAINT fk_medical_history_conditions_history
        FOREIGN KEY (medical_history_id) REFERENCES medical_histories (id) ON DELETE CASCADE,
    CONSTRAINT chk_medical_history_conditions_description
        CHECK (BTRIM(description) <> '')
);

CREATE INDEX idx_medical_history_allergies_history
    ON medical_history_allergies (medical_history_id);
CREATE INDEX idx_medical_history_medications_history
    ON medical_history_medications (medical_history_id);
CREATE INDEX idx_medical_history_conditions_history
    ON medical_history_conditions (medical_history_id);

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'MEDICAL_HISTORY_READ', 'Read patient medical history'),
    (gen_random_uuid(), 'MEDICAL_HISTORY_UPDATE', 'Create or update patient medical history')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE (p.code = 'MEDICAL_HISTORY_READ' AND r.code IN ('ADMINISTRATOR', 'DENTIST', 'ASSISTANT'))
   OR (p.code = 'MEDICAL_HISTORY_UPDATE' AND r.code IN ('DENTIST', 'ASSISTANT'))
ON CONFLICT (role_id, permission_id) DO NOTHING;
