ALTER TABLE users
ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users
SET must_change_password = TRUE
WHERE LOWER(email) IN (LOWER('admin@msr.local'), LOWER('user@msr.local'));
