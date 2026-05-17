DO $$
DECLARE
    configured_primary_email TEXT := NULLIF('${primarySuperAdminEmail}', '');
    configured_setup_admin_email TEXT := NULLIF('${setupAdminEmail}', '');
    configured_setup_user_email TEXT := NULLIF('${setupUserEmail}', '');
BEGIN
    IF configured_primary_email IS NOT NULL
        AND LOWER(configured_primary_email) <> LOWER('wkautsa@gmail.com')
        AND NOT EXISTS (SELECT 1 FROM users WHERE LOWER(email) = LOWER(configured_primary_email))
        AND NOT EXISTS (SELECT 1 FROM users WHERE LOWER(username) = LOWER(configured_primary_email)) THEN
        UPDATE users
        SET email = configured_primary_email,
            username = configured_primary_email,
            temporary = FALSE
        WHERE LOWER(email) = LOWER('wkautsa@gmail.com');
    END IF;

    IF configured_setup_admin_email IS NOT NULL
        AND LOWER(configured_setup_admin_email) <> LOWER('admin@msr.local')
        AND NOT EXISTS (SELECT 1 FROM users WHERE LOWER(email) = LOWER(configured_setup_admin_email)) THEN
        UPDATE users
        SET email = configured_setup_admin_email,
            temporary = TRUE
        WHERE LOWER(email) = LOWER('admin@msr.local');
    END IF;

    IF configured_setup_user_email IS NOT NULL
        AND LOWER(configured_setup_user_email) <> LOWER('user@msr.local')
        AND NOT EXISTS (SELECT 1 FROM users WHERE LOWER(email) = LOWER(configured_setup_user_email)) THEN
        UPDATE users
        SET email = configured_setup_user_email,
            temporary = TRUE
        WHERE LOWER(email) = LOWER('user@msr.local');
    END IF;

    IF configured_primary_email IS NOT NULL THEN
        UPDATE users
        SET temporary = FALSE
        WHERE LOWER(email) = LOWER(configured_primary_email);
    END IF;

    IF configured_setup_admin_email IS NOT NULL THEN
        UPDATE users
        SET temporary = TRUE
        WHERE LOWER(email) = LOWER(configured_setup_admin_email);
    END IF;

    IF configured_setup_user_email IS NOT NULL THEN
        UPDATE users
        SET temporary = TRUE
        WHERE LOWER(email) = LOWER(configured_setup_user_email);
    END IF;
END $$;
