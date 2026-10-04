--liquibase formatted sql

--changeset dentalcare:026-create-billing-cash-shifts
-- One OPEN shift per user. Expected amount and difference are stored only when the shift is closed.
CREATE TABLE billing_cash_shifts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    opening_amount NUMERIC(12, 2) NOT NULL,
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    expected_amount NUMERIC(12, 2),
    counted_amount NUMERIC(12, 2),
    difference NUMERIC(12, 2),
    opening_notes VARCHAR(500),
    closing_notes VARCHAR(500),
    CONSTRAINT fk_billing_cash_shifts_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_billing_cash_shifts_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT chk_billing_cash_shifts_opening_amount CHECK (opening_amount >= 0),
    CONSTRAINT chk_billing_cash_shifts_counted_amount CHECK (counted_amount >= 0),
    CONSTRAINT chk_billing_cash_shifts_consistency CHECK (
        (status = 'OPEN'
            AND closed_at IS NULL
            AND expected_amount IS NULL
            AND counted_amount IS NULL
            AND difference IS NULL)
        OR
        (status = 'CLOSED'
            AND closed_at IS NOT NULL
            AND expected_amount IS NOT NULL
            AND counted_amount IS NOT NULL
            AND difference IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_billing_cash_shifts_open_per_user
    ON billing_cash_shifts (user_id) WHERE status = 'OPEN';

CREATE INDEX idx_billing_cash_shifts_user_opened_at
    ON billing_cash_shifts (user_id, opened_at DESC);

CREATE TABLE billing_cash_movements (
    id UUID PRIMARY KEY,
    cash_shift_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    concept VARCHAR(200) NOT NULL,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_billing_cash_movements_cash_shift
        FOREIGN KEY (cash_shift_id) REFERENCES billing_cash_shifts (id),
    CONSTRAINT fk_billing_cash_movements_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT chk_billing_cash_movements_type CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT chk_billing_cash_movements_amount CHECK (amount > 0),
    CONSTRAINT chk_billing_cash_movements_concept CHECK (BTRIM(concept) <> '')
);

CREATE INDEX idx_billing_cash_movements_shift_created_at
    ON billing_cash_movements (cash_shift_id, created_at);

ALTER TABLE billing_payments ADD COLUMN registered_by_user_id UUID;
ALTER TABLE billing_payments ADD COLUMN cash_shift_id UUID;

ALTER TABLE billing_payments
    ADD CONSTRAINT fk_billing_payments_registered_by_user
        FOREIGN KEY (registered_by_user_id) REFERENCES users (id);

ALTER TABLE billing_payments
    ADD CONSTRAINT fk_billing_payments_cash_shift
        FOREIGN KEY (cash_shift_id) REFERENCES billing_cash_shifts (id);

CREATE INDEX idx_billing_payments_cash_shift ON billing_payments (cash_shift_id);

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'BILLING_CASH_MANAGE', 'Manage cash shifts and manual cash movements'),
    (gen_random_uuid(), 'BILLING_CASH_READ_ALL', 'Read cash shifts of all users')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE (p.code = 'BILLING_CASH_MANAGE' AND r.code IN ('ADMINISTRATOR', 'CASHIER'))
   OR (p.code = 'BILLING_CASH_READ_ALL' AND r.code IN ('ADMINISTRATOR'))
ON CONFLICT (role_id, permission_id) DO NOTHING;
