UPDATE users
SET password_hash = '$2a$10$kw4hWZy7KPjUDM2NbbJIpOnjiKLH/p9OAqHWodfSbniQhwEkbH612',
    must_change_password = FALSE,
    status = 'ACTIVE'
WHERE LOWER(email) IN (
    LOWER('wkautsa@gmail.com'),
    LOWER('admin@msr.local'),
    LOWER('user@msr.local')
);
