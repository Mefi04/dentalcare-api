--liquibase formatted sql

--changeset dentalcare:013-create-sterilization-core
CREATE TABLE sterilization_protocols (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    method VARCHAR(20) NOT NULL,
    description VARCHAR(500),
    instructions VARCHAR(2000) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_sterilization_protocols_name UNIQUE (name),
    CONSTRAINT chk_sterilization_protocols_name CHECK (BTRIM(name) <> ''),
    CONSTRAINT chk_sterilization_protocols_instructions CHECK (BTRIM(instructions) <> ''),
    CONSTRAINT chk_sterilization_protocols_method CHECK (method IN ('STEAM', 'DRY_HEAT', 'CHEMICAL'))
);

CREATE TABLE sterilization_cycles (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    protocol_id UUID NOT NULL,
    responsible_user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    observations VARCHAR(500) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    released_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_sterilization_cycles_code UNIQUE (code),
    CONSTRAINT fk_sterilization_cycles_protocol FOREIGN KEY (protocol_id) REFERENCES sterilization_protocols (id),
    CONSTRAINT fk_sterilization_cycles_responsible FOREIGN KEY (responsible_user_id) REFERENCES users (id),
    CONSTRAINT chk_sterilization_cycles_code CHECK (BTRIM(code) <> ''),
    CONSTRAINT chk_sterilization_cycles_observations CHECK (BTRIM(observations) <> ''),
    CONSTRAINT chk_sterilization_cycles_status CHECK (status IN ('IN_PROGRESS', 'RELEASED')),
    CONSTRAINT chk_sterilization_cycles_release CHECK (
        (status = 'IN_PROGRESS' AND released_at IS NULL)
        OR (status = 'RELEASED' AND released_at IS NOT NULL AND released_at >= started_at)
    )
);

CREATE TABLE sterilization_cycle_instruments (
    cycle_id UUID NOT NULL,
    inventory_item_id UUID NOT NULL,
    CONSTRAINT pk_sterilization_cycle_instruments PRIMARY KEY (cycle_id, inventory_item_id),
    CONSTRAINT fk_sterilization_instruments_cycle FOREIGN KEY (cycle_id) REFERENCES sterilization_cycles (id) ON DELETE CASCADE,
    CONSTRAINT fk_sterilization_instruments_item FOREIGN KEY (inventory_item_id) REFERENCES inventory_items (id)
);

CREATE INDEX idx_sterilization_protocols_active ON sterilization_protocols (active);
CREATE INDEX idx_sterilization_protocols_method ON sterilization_protocols (method);
CREATE INDEX idx_sterilization_cycles_protocol ON sterilization_cycles (protocol_id);
CREATE INDEX idx_sterilization_cycles_responsible ON sterilization_cycles (responsible_user_id);
CREATE INDEX idx_sterilization_cycles_status_started ON sterilization_cycles (status, started_at DESC);
CREATE INDEX idx_sterilization_instruments_item ON sterilization_cycle_instruments (inventory_item_id);

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'STERILIZATION_READ', 'Read sterilization protocols and cycles'),
    (gen_random_uuid(), 'STERILIZATION_WRITE', 'Manage sterilization protocols and cycles')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE (p.code = 'STERILIZATION_READ' AND r.code IN ('ADMINISTRATOR', 'DENTIST', 'ASSISTANT'))
   OR (p.code = 'STERILIZATION_WRITE' AND r.code IN ('ADMINISTRATOR', 'ASSISTANT'))
ON CONFLICT (role_id, permission_id) DO NOTHING;
