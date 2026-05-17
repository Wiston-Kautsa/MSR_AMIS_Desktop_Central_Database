DO $$
DECLARE
    configured_primary_email TEXT := NULLIF('${primarySuperAdminEmail}', '');
    configured_setup_admin_email TEXT := NULLIF('${setupAdminEmail}', '');
    configured_setup_user_email TEXT := NULLIF('${setupUserEmail}', '');
BEGIN
    UPDATE users
    SET status = 'FROZEN',
        temporary = TRUE,
        must_change_password = FALSE,
        reset_code = NULL,
        reset_expiry = NULL,
        reset_requested_at = NULL
    WHERE LOWER(email) IN (
        LOWER('wkautsa@gmail.com'),
        LOWER('admin@msr.local'),
        LOWER('user@msr.local')
    )
    AND (
        configured_primary_email IS NULL
        OR LOWER(email) <> LOWER(configured_primary_email)
    )
    AND (
        configured_setup_admin_email IS NULL
        OR LOWER(email) <> LOWER(configured_setup_admin_email)
    )
    AND (
        configured_setup_user_email IS NULL
        OR LOWER(email) <> LOWER(configured_setup_user_email)
    );
END $$;
