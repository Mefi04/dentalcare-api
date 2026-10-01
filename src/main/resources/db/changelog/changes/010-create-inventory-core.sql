--liquibase formatted sql

--changeset dentalcare:010-create-inventory-core
-- Base catalog table for inventory items (consumables and instruments)
CREATE TABLE inventory_items (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    item_type VARCHAR(20) NOT NULL,
    category VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    unit VARCHAR(50),
    current_stock INTEGER,
    minimum_stock INTEGER,
    expiration_date DATE,
    location VARCHAR(100),
    total_quantity INTEGER,
    available_quantity INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_inventory_items_code UNIQUE (code),
    CONSTRAINT chk_inventory_items_code CHECK (BTRIM(code) <> ''),
    CONSTRAINT chk_inventory_items_name CHECK (BTRIM(name) <> ''),
    CONSTRAINT chk_inventory_items_category CHECK (BTRIM(category) <> ''),
    CONSTRAINT chk_inventory_items_type CHECK (item_type IN ('CONSUMABLE', 'INSTRUMENT')),
    CONSTRAINT chk_inventory_items_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_inventory_items_consumable CHECK (
        item_type <> 'CONSUMABLE' OR (
            unit IS NOT NULL AND BTRIM(unit) <> ''
            AND current_stock IS NOT NULL AND current_stock >= 0
            AND minimum_stock IS NOT NULL AND minimum_stock >= 0
        )
    ),
    CONSTRAINT chk_inventory_items_instrument CHECK (
        item_type <> 'INSTRUMENT' OR (
            location IS NOT NULL AND BTRIM(location) <> ''
            AND total_quantity IS NOT NULL AND total_quantity >= 0
            AND available_quantity IS NOT NULL AND available_quantity >= 0
            AND available_quantity <= total_quantity
        )
    )
);

CREATE INDEX idx_inventory_items_type ON inventory_items (item_type);
CREATE INDEX idx_inventory_items_status ON inventory_items (status);
CREATE INDEX idx_inventory_items_category ON inventory_items (category);
CREATE INDEX idx_inventory_items_name ON inventory_items (name);

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'INVENTORY_READ', 'Read inventory items and stock'),
    (gen_random_uuid(), 'INVENTORY_WRITE', 'Create and update inventory items')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE (p.code = 'INVENTORY_READ' AND r.code IN ('ADMINISTRATOR', 'SECRETARY', 'DENTIST', 'ASSISTANT'))
   OR (p.code = 'INVENTORY_WRITE' AND r.code IN ('ADMINISTRATOR', 'SECRETARY', 'ASSISTANT'))
ON CONFLICT (role_id, permission_id) DO NOTHING;
