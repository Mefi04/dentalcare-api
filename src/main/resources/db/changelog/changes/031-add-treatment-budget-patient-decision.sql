--liquibase formatted sql

--changeset dentalcare:031-add-treatment-budget-patient-decision
ALTER TABLE treatment_budgets
    ADD COLUMN patient_decision VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN patient_decided_by UUID,
    ADD COLUMN patient_decided_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT fk_treatment_budgets_patient_decided_by
        FOREIGN KEY (patient_decided_by) REFERENCES users (id),
    ADD CONSTRAINT chk_treatment_budgets_patient_decision
        CHECK (patient_decision IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    ADD CONSTRAINT chk_treatment_budgets_patient_decision_lifecycle CHECK (
        (patient_decision = 'PENDING' AND patient_decided_by IS NULL AND patient_decided_at IS NULL)
        OR (patient_decision IN ('ACCEPTED', 'REJECTED')
            AND patient_decided_by IS NOT NULL AND patient_decided_at IS NOT NULL)
    ),
    ADD CONSTRAINT chk_treatment_budgets_patient_decision_time
        CHECK (patient_decided_at IS NULL OR patient_decided_at >= created_at);

ALTER TABLE treatment_budgets ALTER COLUMN patient_decision DROP DEFAULT;

CREATE INDEX idx_treatment_budgets_patient_decision_created
    ON treatment_budgets (patient_id, patient_decision, created_at DESC);

--rollback DROP INDEX IF EXISTS idx_treatment_budgets_patient_decision_created;
--rollback ALTER TABLE treatment_budgets DROP CONSTRAINT IF EXISTS chk_treatment_budgets_patient_decision_time;
--rollback ALTER TABLE treatment_budgets DROP CONSTRAINT IF EXISTS chk_treatment_budgets_patient_decision_lifecycle;
--rollback ALTER TABLE treatment_budgets DROP CONSTRAINT IF EXISTS chk_treatment_budgets_patient_decision;
--rollback ALTER TABLE treatment_budgets DROP CONSTRAINT IF EXISTS fk_treatment_budgets_patient_decided_by;
--rollback ALTER TABLE treatment_budgets DROP COLUMN IF EXISTS patient_decided_at;
--rollback ALTER TABLE treatment_budgets DROP COLUMN IF EXISTS patient_decided_by;
--rollback ALTER TABLE treatment_budgets DROP COLUMN IF EXISTS patient_decision;
