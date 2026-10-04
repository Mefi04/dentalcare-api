--liquibase formatted sql

--changeset dentalcare:025-create-treatment-budgets-consents
CREATE TABLE treatment_budgets (
    id UUID PRIMARY KEY,
    treatment_plan_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    version INTEGER NOT NULL,
    plan_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    subtotal NUMERIC(12, 2) NOT NULL,
    total NUMERIC(12, 2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    generated_by UUID NOT NULL,
    decided_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_treatment_budgets_plan FOREIGN KEY (treatment_plan_id) REFERENCES treatment_plans (id),
    CONSTRAINT fk_treatment_budgets_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_treatment_budgets_generated_by FOREIGN KEY (generated_by) REFERENCES users (id),
    CONSTRAINT fk_treatment_budgets_decided_by FOREIGN KEY (decided_by) REFERENCES users (id),
    CONSTRAINT uq_treatment_budgets_plan_version UNIQUE (treatment_plan_id, version),
    CONSTRAINT chk_treatment_budgets_version CHECK (version > 0),
    CONSTRAINT chk_treatment_budgets_amounts CHECK (subtotal >= 0 AND total >= 0 AND total = subtotal),
    CONSTRAINT chk_treatment_budgets_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_treatment_budgets_decision CHECK (
        (status IN ('APPROVED', 'REJECTED') AND decided_by IS NOT NULL AND decided_at IS NOT NULL)
        OR (status = 'PENDING' AND decided_by IS NULL AND decided_at IS NULL)
    ),
    CONSTRAINT chk_treatment_budgets_timestamps CHECK (updated_at >= created_at AND (decided_at IS NULL OR decided_at >= created_at))
);

CREATE TABLE treatment_budget_items (
    id UUID PRIMARY KEY,
    treatment_budget_id UUID NOT NULL,
    treatment_plan_item_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    tooth VARCHAR(20),
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    subtotal NUMERIC(12, 2) NOT NULL,
    position INTEGER NOT NULL,
    CONSTRAINT fk_treatment_budget_items_budget FOREIGN KEY (treatment_budget_id)
        REFERENCES treatment_budgets (id) ON DELETE CASCADE,
    CONSTRAINT fk_treatment_budget_items_plan_item FOREIGN KEY (treatment_plan_item_id)
        REFERENCES treatment_plan_items (id),
    CONSTRAINT uq_treatment_budget_items_position UNIQUE (treatment_budget_id, position),
    CONSTRAINT chk_treatment_budget_items_name CHECK (BTRIM(name) <> ''),
    CONSTRAINT chk_treatment_budget_items_tooth CHECK (tooth IS NULL OR BTRIM(tooth) <> ''),
    CONSTRAINT chk_treatment_budget_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_treatment_budget_items_price CHECK (unit_price > 0),
    CONSTRAINT chk_treatment_budget_items_subtotal CHECK (subtotal = unit_price * quantity),
    CONSTRAINT chk_treatment_budget_items_position CHECK (position >= 0)
);

CREATE TABLE treatment_consents (
    id UUID PRIMARY KEY,
    treatment_plan_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    treatment_budget_id UUID,
    document_version VARCHAR(80) NOT NULL,
    consent_text VARCHAR(20000) NOT NULL,
    status VARCHAR(30) NOT NULL,
    prepared_by UUID NOT NULL,
    accepted_by UUID,
    revoked_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_treatment_consents_plan FOREIGN KEY (treatment_plan_id) REFERENCES treatment_plans (id),
    CONSTRAINT fk_treatment_consents_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_treatment_consents_budget FOREIGN KEY (treatment_budget_id) REFERENCES treatment_budgets (id),
    CONSTRAINT fk_treatment_consents_prepared_by FOREIGN KEY (prepared_by) REFERENCES users (id),
    CONSTRAINT fk_treatment_consents_accepted_by FOREIGN KEY (accepted_by) REFERENCES users (id),
    CONSTRAINT fk_treatment_consents_revoked_by FOREIGN KEY (revoked_by) REFERENCES users (id),
    CONSTRAINT chk_treatment_consents_version CHECK (BTRIM(document_version) <> ''),
    CONSTRAINT chk_treatment_consents_text CHECK (BTRIM(consent_text) <> ''),
    CONSTRAINT chk_treatment_consents_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED')),
    CONSTRAINT chk_treatment_consents_lifecycle CHECK (
        (status = 'PENDING' AND accepted_by IS NULL AND accepted_at IS NULL AND revoked_by IS NULL AND revoked_at IS NULL)
        OR (status = 'ACCEPTED' AND accepted_by IS NOT NULL AND accepted_at IS NOT NULL AND revoked_by IS NULL AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND revoked_by IS NOT NULL AND revoked_at IS NOT NULL)
    ),
    CONSTRAINT chk_treatment_consents_timestamps CHECK (
        updated_at >= created_at
        AND (accepted_at IS NULL OR accepted_at >= created_at)
        AND (revoked_at IS NULL OR revoked_at >= created_at)
    )
);

CREATE UNIQUE INDEX uq_treatment_budgets_active_plan ON treatment_budgets (treatment_plan_id)
    WHERE status IN ('PENDING', 'APPROVED');
CREATE INDEX idx_treatment_budgets_plan_history ON treatment_budgets (treatment_plan_id, version DESC);
CREATE INDEX idx_treatment_budgets_patient ON treatment_budgets (patient_id, created_at DESC);
CREATE INDEX idx_treatment_budget_items_budget ON treatment_budget_items (treatment_budget_id, position);
CREATE UNIQUE INDEX uq_treatment_consents_active_plan ON treatment_consents (treatment_plan_id)
    WHERE status IN ('PENDING', 'ACCEPTED');
CREATE INDEX idx_treatment_consents_plan_history ON treatment_consents (treatment_plan_id, created_at DESC);
CREATE INDEX idx_treatment_consents_patient ON treatment_consents (patient_id, created_at DESC);

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'TREATMENT_BUDGET_READ', 'Read treatment plan budget history and detail'),
    (gen_random_uuid(), 'TREATMENT_BUDGET_CREATE', 'Generate budgets from approved treatment plans'),
    (gen_random_uuid(), 'TREATMENT_BUDGET_DECIDE', 'Approve or reject pending treatment budgets'),
    (gen_random_uuid(), 'TREATMENT_CONSENT_READ', 'Read treatment consent history and detail'),
    (gen_random_uuid(), 'TREATMENT_CONSENT_CREATE', 'Prepare versioned treatment consent documents'),
    (gen_random_uuid(), 'TREATMENT_CONSENT_ACCEPT', 'Record explicit acceptance of treatment consent'),
    (gen_random_uuid(), 'TREATMENT_CONSENT_REVOKE', 'Revoke treatment consent')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE (p.code IN ('TREATMENT_BUDGET_READ', 'TREATMENT_CONSENT_READ')
       AND r.code IN ('ADMINISTRATOR', 'DENTIST', 'ASSISTANT'))
   OR (p.code IN ('TREATMENT_BUDGET_CREATE', 'TREATMENT_CONSENT_CREATE')
       AND r.code IN ('DENTIST', 'ASSISTANT'))
   OR (p.code IN ('TREATMENT_BUDGET_DECIDE', 'TREATMENT_CONSENT_ACCEPT', 'TREATMENT_CONSENT_REVOKE')
       AND r.code = 'DENTIST')
ON CONFLICT (role_id, permission_id) DO NOTHING;

--rollback DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE code IN ('TREATMENT_BUDGET_READ', 'TREATMENT_BUDGET_CREATE', 'TREATMENT_BUDGET_DECIDE', 'TREATMENT_CONSENT_READ', 'TREATMENT_CONSENT_CREATE', 'TREATMENT_CONSENT_ACCEPT', 'TREATMENT_CONSENT_REVOKE'));
--rollback DELETE FROM permissions WHERE code IN ('TREATMENT_BUDGET_READ', 'TREATMENT_BUDGET_CREATE', 'TREATMENT_BUDGET_DECIDE', 'TREATMENT_CONSENT_READ', 'TREATMENT_CONSENT_CREATE', 'TREATMENT_CONSENT_ACCEPT', 'TREATMENT_CONSENT_REVOKE');
--rollback DROP TABLE treatment_consents;
--rollback DROP TABLE treatment_budget_items;
--rollback DROP TABLE treatment_budgets;
