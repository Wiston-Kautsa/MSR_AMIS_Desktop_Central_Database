DO $$
DECLARE
    configured_primary_email TEXT := NULLIF('${primarySuperAdminEmail}', '');
BEGIN
    IF configured_primary_email IS NOT NULL THEN
        UPDATE users
        SET role = 'SUPER_ADMIN',
            status = 'ACTIVE',
            temporary = FALSE
        WHERE LOWER(email) = LOWER(configured_primary_email);
    END IF;
END $$;
