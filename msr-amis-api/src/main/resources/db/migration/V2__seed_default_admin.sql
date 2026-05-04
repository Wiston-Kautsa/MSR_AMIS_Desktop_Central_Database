INSERT INTO departments(name)
VALUES ('MSR')
ON CONFLICT (name) DO NOTHING;

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
    'System Administrator',
    'admin',
    '$2a$10$BvQ5m0M2bOmrZX7rK9N5M.2A2wL7U2JnV4A8M5VQ0Xh95W5AqLqT2',
    'SUPER_ADMIN',
    'ACTIVE',
    'MSR',
    NULL,
    'admin@msr.local'
)
ON CONFLICT (email) DO NOTHING;
