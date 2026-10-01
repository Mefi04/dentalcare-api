--liquibase formatted sql

--changeset dentalcare:017-create-inventory-suppliers-purchases
CREATE TABLE IF NOT EXISTS inventory_suppliers (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    contact_name VARCHAR(150),
    phone VARCHAR(30),
    email VARCHAR(150),
    address VARCHAR(300),
    notes VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_inventory_suppliers_name CHECK (BTRIM(name) <> ''),
    CONSTRAINT chk_inventory_suppliers_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_inventory_suppliers_status ON inventory_suppliers (status);
CREATE INDEX IF NOT EXISTS idx_inventory_suppliers_name ON inventory_suppliers (name);

CREATE SEQUENCE IF NOT EXISTS inventory_purchase_code_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS inventory_purchases (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    supplier_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    purchase_date DATE NOT NULL,
    reference VARCHAR(100),
    observation VARCHAR(500),
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_by UUID,
    received_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_inventory_purchases_supplier
        FOREIGN KEY (supplier_id) REFERENCES inventory_suppliers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_purchases_created_by
        FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_purchases_received_by
        FOREIGN KEY (received_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_inventory_purchases_code CHECK (BTRIM(code) <> ''),
    CONSTRAINT chk_inventory_purchases_status CHECK (status IN ('PENDING', 'RECEIVED')),
    CONSTRAINT chk_inventory_purchases_receipt CHECK (
        (status = 'PENDING' AND received_at IS NULL AND received_by IS NULL)
        OR (status = 'RECEIVED' AND received_at IS NOT NULL AND received_by IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_inventory_purchases_supplier ON inventory_purchases (supplier_id);
CREATE INDEX IF NOT EXISTS idx_inventory_purchases_status ON inventory_purchases (status);
CREATE INDEX IF NOT EXISTS idx_inventory_purchases_created_at ON inventory_purchases (created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_inventory_purchases_date ON inventory_purchases (purchase_date DESC);

CREATE TABLE IF NOT EXISTS inventory_purchase_items (
    id UUID PRIMARY KEY,
    purchase_id UUID NOT NULL,
    inventory_item_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    unit_cost NUMERIC(12, 2) NOT NULL,
    CONSTRAINT fk_inventory_purchase_items_purchase
        FOREIGN KEY (purchase_id) REFERENCES inventory_purchases (id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_purchase_items_item
        FOREIGN KEY (inventory_item_id) REFERENCES inventory_items (id) ON DELETE RESTRICT,
    CONSTRAINT uq_inventory_purchase_items_item
        UNIQUE (purchase_id, inventory_item_id),
    CONSTRAINT chk_inventory_purchase_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_inventory_purchase_items_unit_cost CHECK (unit_cost >= 0)
);

CREATE INDEX IF NOT EXISTS idx_inventory_purchase_items_purchase ON inventory_purchase_items (purchase_id);
CREATE INDEX IF NOT EXISTS idx_inventory_purchase_items_item ON inventory_purchase_items (inventory_item_id);
