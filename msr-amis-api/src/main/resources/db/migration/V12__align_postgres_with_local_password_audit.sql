ALTER TABLE users
ADD COLUMN IF NOT EXISTS reset_requested_at TIMESTAMPTZ;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS last_password_reset TIMESTAMPTZ;

ALTER TABLE audit_log
ADD COLUMN IF NOT EXISTS username VARCHAR(150);

ALTER TABLE audit_log
ADD COLUMN IF NOT EXISTS module_name VARCHAR(100);

UPDATE audit_log
SET username = performed_by
WHERE (username IS NULL OR TRIM(username) = '')
  AND performed_by IS NOT NULL
  AND TRIM(performed_by) <> '';

UPDATE audit_log
SET module_name = entity
WHERE (module_name IS NULL OR TRIM(module_name) = '')
  AND entity IS NOT NULL
  AND TRIM(entity) <> '';

CREATE TABLE IF NOT EXISTS password_reset_audit (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    identifier VARCHAR(150),
    event_type VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_password_reset_audit_user_id
ON password_reset_audit(user_id);

CREATE INDEX IF NOT EXISTS idx_password_reset_audit_identifier
ON password_reset_audit(identifier);
