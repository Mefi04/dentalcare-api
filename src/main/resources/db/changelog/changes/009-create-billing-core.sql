--liquibase formatted sql

--changeset dentalcare:009-create-billing-core
-- Paid amounts, charge status and account balance are derived from these rows; they are never stored.
CREATE TABLE billing_charges (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    concept VARCHAR(200) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_billing_charges_id_patient UNIQUE (id, patient_id),
    CONSTRAINT fk_billing_charges_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT chk_billing_charges_concept CHECK (BTRIM(concept) <> ''),
    CONSTRAINT chk_billing_charges_amount CHECK (amount > 0)
);

-- The composite foreign key guarantees that a payment applied to a charge belongs to the same patient.
-- Advances have no charge; with a NULL charge_id the composite key is not evaluated.
CREATE TABLE billing_payments (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    charge_id UUID,
    kind VARCHAR(30) NOT NULL,
    method VARCHAR(30) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_billing_payments_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_billing_payments_charge_patient
        FOREIGN KEY (charge_id, patient_id) REFERENCES billing_charges (id, patient_id),
    CONSTRAINT chk_billing_payments_kind
        CHECK (kind IN ('PAYMENT', 'PARTIAL_PAYMENT', 'ADVANCE')),
    CONSTRAINT chk_billing_payments_method
        CHECK (method IN ('CASH', 'CARD', 'TRANSFER', 'CHECK')),
    CONSTRAINT chk_billing_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_billing_payments_kind_charge
        CHECK ((kind = 'ADVANCE') = (charge_id IS NULL))
);

CREATE INDEX idx_billing_charges_patient_created_at ON billing_charges (patient_id, created_at);
CREATE INDEX idx_billing_payments_patient_created_at ON billing_payments (patient_id, created_at);
CREATE INDEX idx_billing_payments_charge ON billing_payments (charge_id);

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'BILLING_READ', 'Read patient account statements'),
    (gen_random_uuid(), 'BILLING_CHARGE_CREATE', 'Register charges on patient accounts'),
    (gen_random_uuid(), 'BILLING_PAYMENT_CREATE', 'Register payments on patient accounts')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE (p.code = 'BILLING_READ' AND r.code IN ('ADMINISTRATOR', 'SECRETARY', 'CASHIER'))
   OR (p.code IN ('BILLING_CHARGE_CREATE', 'BILLING_PAYMENT_CREATE') AND r.code IN ('ADMINISTRATOR', 'CASHIER'))
ON CONFLICT (role_id, permission_id) DO NOTHING;
