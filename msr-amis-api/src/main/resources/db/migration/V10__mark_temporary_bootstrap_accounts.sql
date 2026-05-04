ALTER TABLE users
ADD COLUMN IF NOT EXISTS temporary BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users
SET temporary = TRUE
WHERE LOWER(email) IN (
    LOWER('admin@msr.local'),
    LOWER('user@msr.local'),
    LOWER('wkautsa@gmail.com')
);

UPDATE users
SET temporary = FALSE
WHERE LOWER(email) NOT IN (
    LOWER('admin@msr.local'),
    LOWER('user@msr.local'),
    LOWER('wkautsa@gmail.com')
);
