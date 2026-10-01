--liquibase formatted sql

--changeset dentalcare:013-create-clinical-records-core
CREATE TABLE IF NOT EXISTS clinical_attentions (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    professional_id UUID NOT NULL,
    appointment_id UUID,
    reason VARCHAR(200) NOT NULL,
    clinical_notes VARCHAR(4000) NOT NULL,
    next_steps VARCHAR(1000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_clinical_attentions_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_clinical_attentions_professional
        FOREIGN KEY (professional_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_clinical_attentions_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments (id) ON DELETE SET NULL,
    CONSTRAINT chk_clinical_attentions_reason CHECK (BTRIM(reason) <> ''),
    CONSTRAINT chk_clinical_attentions_notes CHECK (BTRIM(clinical_notes) <> ''),
    CONSTRAINT chk_clinical_attentions_next_steps CHECK (next_steps IS NULL OR BTRIM(next_steps) <> '')
);

CREATE TABLE IF NOT EXISTS clinical_diagnoses (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    attention_id UUID NOT NULL,
    treatment_plan_id UUID,
    author_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    description VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_clinical_diagnoses_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_clinical_diagnoses_attention
        FOREIGN KEY (attention_id) REFERENCES clinical_attentions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_clinical_diagnoses_treatment_plan
        FOREIGN KEY (treatment_plan_id) REFERENCES treatment_plans (id) ON DELETE SET NULL,
    CONSTRAINT fk_clinical_diagnoses_author
        FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_clinical_diagnoses_type CHECK (type IN ('PRIMARY', 'SECONDARY')),
    CONSTRAINT chk_clinical_diagnoses_description CHECK (BTRIM(description) <> '')
);

CREATE TABLE IF NOT EXISTS clinical_evolution_notes (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    attention_id UUID NOT NULL,
    author_id UUID NOT NULL,
    consultation_date DATE NOT NULL,
    procedure_summary VARCHAR(300) NOT NULL,
    note VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_clinical_evolution_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_clinical_evolution_attention
        FOREIGN KEY (attention_id) REFERENCES clinical_attentions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_clinical_evolution_author
        FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_clinical_evolution_procedure CHECK (BTRIM(procedure_summary) <> ''),
    CONSTRAINT chk_clinical_evolution_note CHECK (BTRIM(note) <> '')
);

CREATE TABLE IF NOT EXISTS odontogram_findings (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    attention_id UUID,
    author_id UUID NOT NULL,
    dentition VARCHAR(20) NOT NULL,
    tooth_code VARCHAR(10) NOT NULL,
    surface VARCHAR(20),
    finding VARCHAR(30) NOT NULL,
    observation VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_odontogram_findings_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_odontogram_findings_attention
        FOREIGN KEY (attention_id) REFERENCES clinical_attentions (id) ON DELETE SET NULL,
    CONSTRAINT fk_odontogram_findings_author
        FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_odontogram_findings_dentition CHECK (dentition IN ('ADULT', 'MIXED', 'CHILD')),
    CONSTRAINT chk_odontogram_findings_tooth_code CHECK (BTRIM(tooth_code) <> ''),
    CONSTRAINT chk_odontogram_findings_surface CHECK (surface IS NULL OR surface IN ('VESTIBULAR', 'PALATAL', 'MESIAL', 'DISTAL', 'OCCLUSAL')),
    CONSTRAINT chk_odontogram_findings_finding CHECK (finding IN ('HEALTHY', 'CARIOUS', 'TREATED', 'MISSING', 'TO_TREAT')),
    CONSTRAINT chk_odontogram_findings_observation CHECK (observation IS NULL OR BTRIM(observation) <> '')
);

CREATE INDEX IF NOT EXISTS idx_clinical_attentions_patient_occurred
    ON clinical_attentions (patient_id, occurred_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_clinical_attentions_professional ON clinical_attentions (professional_id);
CREATE INDEX IF NOT EXISTS idx_clinical_attentions_appointment ON clinical_attentions (appointment_id);

CREATE INDEX IF NOT EXISTS idx_clinical_diagnoses_patient_created
    ON clinical_diagnoses (patient_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_clinical_diagnoses_attention ON clinical_diagnoses (attention_id);
CREATE INDEX IF NOT EXISTS idx_clinical_diagnoses_treatment_plan ON clinical_diagnoses (treatment_plan_id);
CREATE INDEX IF NOT EXISTS idx_clinical_diagnoses_author ON clinical_diagnoses (author_id);

CREATE INDEX IF NOT EXISTS idx_clinical_evolution_patient_consultation
    ON clinical_evolution_notes (patient_id, consultation_date DESC, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_clinical_evolution_attention ON clinical_evolution_notes (attention_id);
CREATE INDEX IF NOT EXISTS idx_clinical_evolution_author ON clinical_evolution_notes (author_id);

CREATE INDEX IF NOT EXISTS idx_odontogram_findings_patient_tooth
    ON odontogram_findings (patient_id, tooth_code, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_odontogram_findings_patient_created
    ON odontogram_findings (patient_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_odontogram_findings_attention ON odontogram_findings (attention_id);
CREATE INDEX IF NOT EXISTS idx_odontogram_findings_author ON odontogram_findings (author_id);

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'CLINICAL_RECORD_READ', 'Read patient clinical record, attentions, diagnoses, evolution, and odontogram'),
    (gen_random_uuid(), 'CLINICAL_RECORD_WRITE', 'Register clinical attentions, diagnoses, evolution notes, and odontogram findings')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE (p.code = 'CLINICAL_RECORD_READ' AND r.code IN ('ADMINISTRATOR', 'DENTIST', 'ASSISTANT'))
   OR (p.code = 'CLINICAL_RECORD_WRITE' AND r.code IN ('DENTIST', 'ASSISTANT'))
ON CONFLICT (role_id, permission_id) DO NOTHING;
