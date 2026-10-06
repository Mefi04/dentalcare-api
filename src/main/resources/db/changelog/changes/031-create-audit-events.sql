--liquibase formatted sql
--changeset dentalcare:031-create-audit-events
CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_user_id UUID NULL REFERENCES users(id),
    actor_display_name VARCHAR(120) NULL,
    action_code VARCHAR(64) NOT NULL,
    module VARCHAR(40) NOT NULL,
    entity_type VARCHAR(60) NULL,
    entity_id VARCHAR(80) NULL,
    result VARCHAR(12) NOT NULL CHECK (result IN ('SUCCESS', 'FAILURE')),
    detail VARCHAR(240) NULL
);
CREATE INDEX idx_audit_events_occurred_at ON audit_events (occurred_at DESC, id DESC);
CREATE INDEX idx_audit_events_actor ON audit_events (actor_user_id, occurred_at DESC);
CREATE INDEX idx_audit_events_module ON audit_events (module, occurred_at DESC);
CREATE INDEX idx_audit_events_action ON audit_events (action_code, occurred_at DESC);
INSERT INTO permissions (id, code, description) VALUES (gen_random_uuid(), 'AUDIT_READ', 'Read immutable audit events') ON CONFLICT (code) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code='ADMINISTRATOR' AND p.code='AUDIT_READ'
ON CONFLICT (role_id, permission_id) DO NOTHING;
--rollback DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE code='AUDIT_READ');
--rollback DELETE FROM permissions WHERE code='AUDIT_READ';
--rollback DROP TABLE audit_events;
