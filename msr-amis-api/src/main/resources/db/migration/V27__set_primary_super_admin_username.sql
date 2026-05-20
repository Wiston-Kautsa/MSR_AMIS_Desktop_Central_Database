DO $$
DECLARE
    configured_primary_email TEXT := NULLIF('${primarySuperAdminEmail}', '');
    desired_username TEXT := 'MSRAMIS Admin';
BEGIN
    IF configured_primary_email IS NULL THEN
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM users
        WHERE LOWER(username) = LOWER(desired_username)
          AND LOWER(email) <> LOWER(configured_primary_email)
    ) THEN
        UPDATE users
        SET username = desired_username,
            full_name = desired_username
        WHERE LOWER(email) = LOWER(configured_primary_email);
    ELSE
        UPDATE users
        SET full_name = desired_username
        WHERE LOWER(email) = LOWER(configured_primary_email);
    END IF;
END $$;
