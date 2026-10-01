--liquibase formatted sql

--changeset dentalcare:016-create-treatment-procedures
CREATE TABLE IF NOT EXISTS treatment_procedures (
    id UUID PRIMARY KEY,
    treatment_plan_id UUID NOT NULL,
    treatment_plan_item_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    professional_id UUID NOT NULL,
    procedure_name VARCHAR(200) NOT NULL,
    tooth VARCHAR(20),
    sequence_number INTEGER NOT NULL,
    clinical_observations VARCHAR(4000),
    completion_notes VARCHAR(4000),
    status VARCHAR(30) NOT NULL,
    performed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_treatment_procedures_plan
        FOREIGN KEY (treatment_plan_id) REFERENCES treatment_plans (id),
    CONSTRAINT fk_treatment_procedures_plan_item
        FOREIGN KEY (treatment_plan_item_id) REFERENCES treatment_plan_items (id),
    CONSTRAINT fk_treatment_procedures_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_treatment_procedures_professional
        FOREIGN KEY (professional_id) REFERENCES users (id),
    CONSTRAINT uq_treatment_procedures_item_sequence
        UNIQUE (treatment_plan_item_id, sequence_number),
    CONSTRAINT chk_treatment_procedures_name CHECK (BTRIM(procedure_name) <> ''),
    CONSTRAINT chk_treatment_procedures_tooth CHECK (tooth IS NULL OR BTRIM(tooth) <> ''),
    CONSTRAINT chk_treatment_procedures_sequence CHECK (sequence_number > 0),
    CONSTRAINT chk_treatment_procedures_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT chk_treatment_procedures_completion CHECK (
        (status = 'IN_PROGRESS' AND completed_at IS NULL)
        OR (status = 'COMPLETED' AND completed_at IS NOT NULL AND completed_at >= performed_at)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_treatment_procedures_active_item
    ON treatment_procedures (treatment_plan_item_id)
    WHERE status = 'IN_PROGRESS';
CREATE INDEX IF NOT EXISTS idx_treatment_procedures_plan_history
    ON treatment_procedures (treatment_plan_id, performed_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_treatment_procedures_patient_history
    ON treatment_procedures (patient_id, performed_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_treatment_procedures_professional
    ON treatment_procedures (professional_id);

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'TREATMENT_PROCEDURE_READ', 'Read treatment procedure execution and history'),
    (gen_random_uuid(), 'TREATMENT_PROCEDURE_EXECUTE', 'Start an approved treatment procedure'),
    (gen_random_uuid(), 'TREATMENT_PROCEDURE_COMPLETE', 'Complete an in-progress treatment procedure')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE (p.code = 'TREATMENT_PROCEDURE_READ'
       AND r.code IN ('ADMINISTRATOR', 'DENTIST', 'ASSISTANT'))
   OR (p.code IN ('TREATMENT_PROCEDURE_EXECUTE', 'TREATMENT_PROCEDURE_COMPLETE')
       AND r.code = 'DENTIST')
ON CONFLICT (role_id, permission_id) DO NOTHING;
