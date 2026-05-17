UPDATE users
SET status = 'FROZEN',
    must_change_password = FALSE,
    reset_code = NULL,
    reset_expiry = NULL,
    reset_requested_at = NULL
WHERE LOWER(email) IN (
    LOWER('admin@msr.local'),
    LOWER('user@msr.local'),
    LOWER('wkautsa@gmail.com')
);
