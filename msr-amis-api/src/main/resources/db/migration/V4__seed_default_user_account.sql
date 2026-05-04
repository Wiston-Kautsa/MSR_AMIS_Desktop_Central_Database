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
    'System Standard User',
    'user',
    '$2a$10$BvQ5m0M2bOmrZX7rK9N5M.2A2wL7U2JnV4A8M5VQ0Xh95W5AqLqT2',
    'USER',
    'ACTIVE',
    'MSR',
    NULL,
    'user@msr.local'
)
ON CONFLICT (email) DO UPDATE
SET full_name = EXCLUDED.full_name,
    username = EXCLUDED.username,
    role = 'USER',
    status = 'ACTIVE',
    department = EXCLUDED.department;
