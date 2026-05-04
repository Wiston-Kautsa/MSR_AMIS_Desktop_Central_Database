INSERT INTO users (
    full_name,
    username,
    password_hash,
    role,
    status,
    department,
    phone,
    email
)
VALUES (
    'W Kautsa',
    'wkautsa@gmail.com',
    '$2a$10$BvQ5m0M2bOmrZX7rK9N5M.2A2wL7U2JnV4A8M5VQ0Xh95W5AqLqT2',
    'SUPER_ADMIN',
    'ACTIVE',
    'MSR',
    NULL,
    'wkautsa@gmail.com'
)
ON CONFLICT (email) DO UPDATE
SET full_name = EXCLUDED.full_name,
    username = EXCLUDED.username,
    role = 'SUPER_ADMIN',
    status = 'ACTIVE',
    department = EXCLUDED.department;

INSERT INTO users (
    full_name,
    username,
    password_hash,
    role,
    status,
    department,
    phone,
    email
)
VALUES (
    'System Setup Administrator',
    'admin',
    '$2a$10$BvQ5m0M2bOmrZX7rK9N5M.2A2wL7U2JnV4A8M5VQ0Xh95W5AqLqT2',
    'ADMIN',
    'ACTIVE',
    'MSR',
    NULL,
    'admin@msr.local'
)
ON CONFLICT (email) DO UPDATE
SET full_name = EXCLUDED.full_name,
    username = EXCLUDED.username,
    role = 'ADMIN',
    status = 'ACTIVE',
    department = EXCLUDED.department;

UPDATE users
SET role = 'ADMIN'
WHERE UPPER(role) = 'SUPER_ADMIN'
  AND LOWER(email) <> LOWER('wkautsa@gmail.com');
