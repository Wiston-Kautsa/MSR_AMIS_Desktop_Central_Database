DO $$
DECLARE
    configured_primary_email TEXT := NULLIF('${primarySuperAdminEmail}', '');
BEGIN
    IF configured_primary_email IS NOT NULL THEN
        UPDATE users
        SET must_change_password = TRUE
        WHERE LOWER(email) = LOWER(configured_primary_email);
    END IF;
END $$;
