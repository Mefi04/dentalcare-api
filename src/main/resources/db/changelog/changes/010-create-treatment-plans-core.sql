--liquibase formatted sql

--changeset dentalcare:010-create-treatment-plans-core
CREATE TABLE treatment_plans (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    professional_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    observations VARCHAR(4000),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_treatment_plans_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_treatment_plans_professional
        FOREIGN KEY (professional_id) REFERENCES users (id),
    CONSTRAINT chk_treatment_plans_name CHECK (BTRIM(name) <> ''),
    CONSTRAINT chk_treatment_plans_status CHECK (status IN ('DRAFT', 'APPROVED')),
    CONSTRAINT chk_treatment_plans_approval
        CHECK ((status = 'APPROVED') = (approved_at IS NOT NULL))
);

CREATE TABLE treatment_plan_items (
    id UUID PRIMARY KEY,
    treatment_plan_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    tooth VARCHAR(20),
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    position INTEGER NOT NULL,
    CONSTRAINT fk_treatment_plan_items_plan
        FOREIGN KEY (treatment_plan_id) REFERENCES treatment_plans (id) ON DELETE CASCADE,
    CONSTRAINT uq_treatment_plan_items_position UNIQUE (treatment_plan_id, position),
    CONSTRAINT chk_treatment_plan_items_name CHECK (BTRIM(name) <> ''),
    CONSTRAINT chk_treatment_plan_items_tooth CHECK (tooth IS NULL OR BTRIM(tooth) <> ''),
    CONSTRAINT chk_treatment_plan_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_treatment_plan_items_unit_price CHECK (unit_price > 0),
    CONSTRAINT chk_treatment_plan_items_position CHECK (position >= 0)
);

CREATE INDEX idx_treatment_plans_patient_created_at
    ON treatment_plans (patient_id, created_at DESC, id DESC);
CREATE INDEX idx_treatment_plans_professional ON treatment_plans (professional_id);
CREATE INDEX idx_treatment_plan_items_plan ON treatment_plan_items (treatment_plan_id);

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'TREATMENT_PLAN_READ', 'Read treatment plans and available professionals'),
    (gen_random_uuid(), 'TREATMENT_PLAN_CREATE', 'Create draft treatment plans'),
    (gen_random_uuid(), 'TREATMENT_PLAN_UPDATE', 'Update draft treatment plans'),
    (gen_random_uuid(), 'TREATMENT_PLAN_APPROVE', 'Approve draft treatment plans')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE (p.code = 'TREATMENT_PLAN_READ' AND r.code IN ('ADMINISTRATOR', 'DENTIST', 'ASSISTANT'))
   OR (p.code IN ('TREATMENT_PLAN_CREATE', 'TREATMENT_PLAN_UPDATE') AND r.code IN ('DENTIST', 'ASSISTANT'))
   OR (p.code = 'TREATMENT_PLAN_APPROVE' AND r.code = 'DENTIST')
ON CONFLICT (role_id, permission_id) DO NOTHING;
