UPDATE users
SET must_change_password = FALSE,
    status = 'ACTIVE'
WHERE LOWER(email) = LOWER('admin@msr.local');
