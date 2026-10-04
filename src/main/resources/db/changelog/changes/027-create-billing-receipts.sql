--liquibase formatted sql

--changeset dentalcare:027-create-billing-receipts
CREATE SEQUENCE billing_receipt_number_seq AS BIGINT START WITH 1 INCREMENT BY 1;

ALTER TABLE billing_payments
    ADD CONSTRAINT uq_billing_payments_id_patient UNIQUE (id, patient_id);

CREATE TABLE billing_receipts (
    id UUID PRIMARY KEY,
    receipt_number BIGINT NOT NULL,
    payment_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    concept VARCHAR(200) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    method VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    issued_by_user_id UUID NOT NULL,
    voided_at TIMESTAMP WITH TIME ZONE,
    void_reason VARCHAR(500),
    CONSTRAINT uq_billing_receipts_number UNIQUE (receipt_number),
    CONSTRAINT uq_billing_receipts_payment UNIQUE (payment_id),
    CONSTRAINT fk_billing_receipts_payment_patient
        FOREIGN KEY (payment_id, patient_id) REFERENCES billing_payments (id, patient_id),
    CONSTRAINT fk_billing_receipts_issued_by
        FOREIGN KEY (issued_by_user_id) REFERENCES users (id),
    CONSTRAINT chk_billing_receipts_concept CHECK (BTRIM(concept) <> ''),
    CONSTRAINT chk_billing_receipts_amount CHECK (amount > 0),
    CONSTRAINT chk_billing_receipts_method CHECK (method IN ('CASH', 'CARD', 'TRANSFER', 'CHECK')),
    CONSTRAINT chk_billing_receipts_status CHECK (status IN ('ISSUED', 'VOID')),
    CONSTRAINT chk_billing_receipts_void CHECK (
        (status = 'ISSUED' AND voided_at IS NULL AND void_reason IS NULL)
        OR
        (status = 'VOID' AND voided_at IS NOT NULL AND void_reason IS NOT NULL)
    )
);

CREATE INDEX idx_billing_receipts_patient_issued_at
    ON billing_receipts (patient_id, issued_at);

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'BILLING_RECEIPT_CREATE', 'Issue a receipt for a patient payment')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE (p.code = 'BILLING_RECEIPT_CREATE' AND r.code IN ('ADMINISTRATOR', 'CASHIER'))
ON CONFLICT (role_id, permission_id) DO NOTHING;
