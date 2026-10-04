--liquibase formatted sql

--changeset dentalcare:028-create-billing-payment-plans
CREATE TABLE billing_payment_plans (
    id UUID PRIMARY KEY,
    charge_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    installments_count INTEGER NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    baseline_paid NUMERIC(12, 2) NOT NULL,
    first_due_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_user_id UUID NOT NULL,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    cancelled_by_user_id UUID,
    cancel_reason VARCHAR(500),
    CONSTRAINT fk_billing_payment_plans_charge_patient
        FOREIGN KEY (charge_id, patient_id) REFERENCES billing_charges (id, patient_id),
    CONSTRAINT fk_billing_payment_plans_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_billing_payment_plans_cancelled_by
        FOREIGN KEY (cancelled_by_user_id) REFERENCES users (id),
    CONSTRAINT chk_billing_payment_plans_installments_count CHECK (installments_count BETWEEN 2 AND 60),
    CONSTRAINT chk_billing_payment_plans_total_amount CHECK (total_amount > 0),
    CONSTRAINT chk_billing_payment_plans_baseline_paid CHECK (baseline_paid >= 0),
    CONSTRAINT chk_billing_payment_plans_status CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT chk_billing_payment_plans_cancel CHECK (
        (status = 'ACTIVE'
            AND cancelled_at IS NULL
            AND cancelled_by_user_id IS NULL
            AND cancel_reason IS NULL)
        OR
        (status = 'CANCELLED'
            AND cancelled_at IS NOT NULL
            AND cancelled_by_user_id IS NOT NULL
            AND cancel_reason IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_billing_payment_plans_active_charge
    ON billing_payment_plans (charge_id) WHERE status = 'ACTIVE';

CREATE INDEX idx_billing_payment_plans_patient_created_at
    ON billing_payment_plans (patient_id, created_at);

CREATE TABLE billing_installments (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL,
    number INTEGER NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    due_date DATE NOT NULL,
    CONSTRAINT fk_billing_installments_plan
        FOREIGN KEY (plan_id) REFERENCES billing_payment_plans (id),
    CONSTRAINT uq_billing_installments_plan_number UNIQUE (plan_id, number),
    CONSTRAINT chk_billing_installments_number CHECK (number >= 1),
    CONSTRAINT chk_billing_installments_amount CHECK (amount > 0)
);

CREATE INDEX idx_billing_installments_plan
    ON billing_installments (plan_id);

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'BILLING_PLAN_MANAGE', 'Create and cancel payment plans')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE (p.code = 'BILLING_PLAN_MANAGE' AND r.code IN ('ADMINISTRATOR', 'CASHIER'))
ON CONFLICT (role_id, permission_id) DO NOTHING;
