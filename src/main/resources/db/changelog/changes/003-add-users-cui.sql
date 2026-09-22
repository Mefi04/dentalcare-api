--liquibase formatted sql

--changeset dentalcare:003-add-users-cui
ALTER TABLE users ADD COLUMN cui VARCHAR(13);

-- Existing rows receive deterministic, non-real placeholder CUIs so the migration can be applied safely.
-- Replace placeholders with verified account CUIs before enabling those accounts in production.
WITH numbered_users AS (
    SELECT id, LPAD(ROW_NUMBER() OVER (ORDER BY id)::TEXT, 13, '0') AS generated_cui
    FROM users
    WHERE cui IS NULL
)
UPDATE users AS u
SET cui = numbered_users.generated_cui
FROM numbered_users
WHERE u.id = numbered_users.id;

ALTER TABLE users ALTER COLUMN cui SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_cui UNIQUE (cui);
ALTER TABLE users ADD CONSTRAINT chk_users_cui_format CHECK (cui ~ '^[0-9]{13}$');
