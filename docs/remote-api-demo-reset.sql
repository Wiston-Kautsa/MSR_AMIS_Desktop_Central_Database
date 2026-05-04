BEGIN;

DELETE FROM returns;
DELETE FROM distribution;
DELETE FROM assignments;
DELETE FROM equipment;
DELETE FROM audit_log;

ALTER SEQUENCE returns_id_seq RESTART WITH 1;
ALTER SEQUENCE distribution_id_seq RESTART WITH 1;
ALTER SEQUENCE assignments_id_seq RESTART WITH 1;
ALTER SEQUENCE equipment_id_seq RESTART WITH 1;
ALTER SEQUENCE audit_log_id_seq RESTART WITH 1;

COMMIT;

-- Optional hard reset for user accounts:
-- DELETE FROM users
-- WHERE LOWER(email) NOT IN (
--     LOWER('admin@msr.local'),
--     LOWER('user@msr.local'),
--     LOWER('wkautsa@gmail.com')
-- );
