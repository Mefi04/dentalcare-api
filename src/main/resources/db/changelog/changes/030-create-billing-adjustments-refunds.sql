--liquibase formatted sql

--changeset dentalcare:030-create-billing-adjustments-refunds
CREATE TABLE billing_charge_adjustments (
    id UUID PRIMARY KEY,
    charge_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount NUMERIC(12, 2),
    reason VARCHAR(500) NOT NULL,
    requested_by_user_id UUID NOT NULL,
    authorized_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_billing_charge_adjustments_charge_patient
        FOREIGN KEY (charge_id, patient_id) REFERENCES billing_charges (id, patient_id),
    CONSTRAINT fk_billing_charge_adjustments_requested_by
        FOREIGN KEY (requested_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_billing_charge_adjustments_authorized_by
        FOREIGN KEY (authorized_by_user_id) REFERENCES users (id),
    CONSTRAINT chk_billing_charge_adjustments_type CHECK (type IN ('DISCOUNT', 'VOID')),
    CONSTRAINT chk_billing_charge_adjustments_amount CHECK (
        (type = 'DISCOUNT' AND amount IS NOT NULL AND amount > 0)
        OR (type = 'VOID' AND amount IS NULL)
    ),
    CONSTRAINT chk_billing_charge_adjustments_reason CHECK (BTRIM(reason) <> '')
);

CREATE UNIQUE INDEX uq_billing_charge_adjustments_void_per_charge
    ON billing_charge_adjustments (charge_id)
    WHERE type = 'VOID';

CREATE INDEX idx_billing_charge_adjustments_charge
    ON billing_charge_adjustments (charge_id);

CREATE INDEX idx_billing_charge_adjustments_patient_created_at
    ON billing_charge_adjustments (patient_id, created_at);

CREATE TABLE billing_refunds (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    requested_by_user_id UUID NOT NULL,
    authorized_by_user_id UUID NOT NULL,
    cash_shift_id UUID,
    idempotency_key VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_billing_refunds_payment_patient
        FOREIGN KEY (payment_id, patient_id) REFERENCES billing_payments (id, patient_id),
    CONSTRAINT fk_billing_refunds_requested_by
        FOREIGN KEY (requested_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_billing_refunds_authorized_by
        FOREIGN KEY (authorized_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_billing_refunds_cash_shift
        FOREIGN KEY (cash_shift_id) REFERENCES billing_cash_shifts (id),
    CONSTRAINT uq_billing_refunds_idempotency UNIQUE (requested_by_user_id, idempotency_key),
    CONSTRAINT chk_billing_refunds_amount CHECK (amount > 0),
    CONSTRAINT chk_billing_refunds_reason CHECK (BTRIM(reason) <> '')
);

CREATE INDEX idx_billing_refunds_payment
    ON billing_refunds (payment_id);

CREATE INDEX idx_billing_refunds_patient_created_at
    ON billing_refunds (patient_id, created_at);

CREATE INDEX idx_billing_refunds_cash_shift
    ON billing_refunds (cash_shift_id);

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'BILLING_ADJUSTMENT_CREATE', 'Create charge discounts and voids')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE (p.code = 'BILLING_ADJUSTMENT_CREATE' AND r.code IN ('ADMINISTRATOR'))
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'BILLING_REFUND_CREATE', 'Create payment refunds')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE (p.code = 'BILLING_REFUND_CREATE' AND r.code IN ('ADMINISTRATOR'))
ON CONFLICT (role_id, permission_id) DO NOTHING;
