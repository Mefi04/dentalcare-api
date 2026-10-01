--liquibase formatted sql

--changeset dentalcare:012-create-inventory-movements
CREATE TABLE IF NOT EXISTS inventory_movements (
    id UUID PRIMARY KEY,
    inventory_item_id UUID NOT NULL,
    movement_type VARCHAR(20) NOT NULL,
    quantity INTEGER NOT NULL,
    stock_before INTEGER NOT NULL,
    stock_after INTEGER NOT NULL,
    available_before INTEGER,
    available_after INTEGER,
    performed_by UUID NOT NULL,
    observation VARCHAR(500),
    reference VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_inventory_movements_item
        FOREIGN KEY (inventory_item_id) REFERENCES inventory_items (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_movements_user
        FOREIGN KEY (performed_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_inventory_movements_type
        CHECK (movement_type IN ('ENTRY', 'EXIT', 'ADJUSTMENT')),
    CONSTRAINT chk_inventory_movements_quantity CHECK (quantity > 0),
    CONSTRAINT chk_inventory_movements_stock CHECK (stock_before >= 0 AND stock_after >= 0),
    CONSTRAINT chk_inventory_movements_availability CHECK (
        (available_before IS NULL AND available_after IS NULL)
        OR (
            available_before IS NOT NULL AND available_after IS NOT NULL
            AND available_before >= 0 AND available_after >= 0
            AND available_before <= stock_before AND available_after <= stock_after
        )
    )
);

CREATE INDEX IF NOT EXISTS idx_inventory_movements_item_created
    ON inventory_movements (inventory_item_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_inventory_movements_type ON inventory_movements (movement_type);
CREATE INDEX IF NOT EXISTS idx_inventory_movements_performed_by ON inventory_movements (performed_by);
CREATE INDEX IF NOT EXISTS idx_inventory_movements_created_at ON inventory_movements (created_at DESC);
