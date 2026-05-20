DO $$
DECLARE
    configured_setup_admin_email TEXT := NULLIF('${setupAdminEmail}', '');
    configured_setup_user_email TEXT := NULLIF('${setupUserEmail}', '');
BEGIN
    IF configured_setup_admin_email IS NOT NULL THEN
        UPDATE users
        SET status = 'ACTIVE',
            temporary = TRUE,
            must_change_password = FALSE,
            reset_code = NULL,
            reset_expiry = NULL,
            reset_requested_at = NULL
        WHERE LOWER(email) = LOWER(configured_setup_admin_email);
    END IF;

    IF configured_setup_user_email IS NOT NULL THEN
        UPDATE users
        SET status = 'ACTIVE',
            temporary = TRUE,
            must_change_password = FALSE,
            reset_code = NULL,
            reset_expiry = NULL,
            reset_requested_at = NULL
        WHERE LOWER(email) = LOWER(configured_setup_user_email);
    END IF;
END $$;
