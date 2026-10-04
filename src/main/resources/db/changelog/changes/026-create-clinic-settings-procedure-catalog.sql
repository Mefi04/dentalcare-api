--liquibase formatted sql

--changeset dentalcare:026-create-clinic-settings-procedure-catalog
CREATE TABLE clinic_settings (
    id SMALLINT PRIMARY KEY,
    trade_name VARCHAR(150),
    nit VARCHAR(20),
    phone VARCHAR(30),
    email VARCHAR(255),
    address VARCHAR(255),
    city VARCHAR(100),
    business_hours VARCHAR(255),
    receipt_prefix VARCHAR(20),
    updated_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_clinic_settings_singleton CHECK (id = 1),
    CONSTRAINT chk_clinic_settings_trade_name CHECK (trade_name IS NULL OR BTRIM(trade_name) <> ''),
    CONSTRAINT chk_clinic_settings_nit CHECK (nit IS NULL OR BTRIM(nit) <> ''),
    CONSTRAINT chk_clinic_settings_phone CHECK (phone IS NULL OR BTRIM(phone) <> ''),
    CONSTRAINT chk_clinic_settings_email CHECK (email IS NULL OR BTRIM(email) <> ''),
    CONSTRAINT chk_clinic_settings_address CHECK (address IS NULL OR BTRIM(address) <> ''),
    CONSTRAINT chk_clinic_settings_city CHECK (city IS NULL OR BTRIM(city) <> ''),
    CONSTRAINT chk_clinic_settings_business_hours CHECK (business_hours IS NULL OR BTRIM(business_hours) <> ''),
    CONSTRAINT chk_clinic_settings_receipt_prefix CHECK (receipt_prefix IS NULL OR BTRIM(receipt_prefix) <> ''),
    CONSTRAINT chk_clinic_settings_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT fk_clinic_settings_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE RESTRICT
);

INSERT INTO clinic_settings (id, created_at, updated_at)
VALUES (1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

CREATE TABLE procedure_catalog_items (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(100) NOT NULL,
    duration_minutes INTEGER NOT NULL,
    base_price NUMERIC(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_procedure_catalog_code CHECK (BTRIM(code) <> ''),
    CONSTRAINT chk_procedure_catalog_name CHECK (BTRIM(name) <> ''),
    CONSTRAINT chk_procedure_catalog_category CHECK (BTRIM(category) <> ''),
    CONSTRAINT chk_procedure_catalog_duration CHECK (duration_minutes BETWEEN 5 AND 480),
    CONSTRAINT chk_procedure_catalog_price CHECK (base_price > 0),
    CONSTRAINT chk_procedure_catalog_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_procedure_catalog_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT fk_procedure_catalog_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_procedure_catalog_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_procedure_catalog_code_ci ON procedure_catalog_items (LOWER(BTRIM(code)));
CREATE UNIQUE INDEX uq_procedure_catalog_name_ci ON procedure_catalog_items (LOWER(BTRIM(name)));
CREATE INDEX idx_procedure_catalog_status ON procedure_catalog_items (status);
CREATE INDEX idx_procedure_catalog_category_ci ON procedure_catalog_items (LOWER(category));
CREATE INDEX idx_procedure_catalog_name_ci ON procedure_catalog_items (LOWER(name));

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'SETTINGS_READ', 'Read clinic settings and operational procedure catalog'),
    (gen_random_uuid(), 'SETTINGS_WRITE', 'Manage clinic settings and operational procedure catalog')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'ADMINISTRATOR'
  AND p.code IN ('SETTINGS_READ', 'SETTINGS_WRITE')
ON CONFLICT (role_id, permission_id) DO NOTHING;

--rollback DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE code IN ('SETTINGS_READ', 'SETTINGS_WRITE'));
--rollback DELETE FROM permissions WHERE code IN ('SETTINGS_READ', 'SETTINGS_WRITE');
--rollback DROP TABLE procedure_catalog_items;
--rollback DROP TABLE clinic_settings;
