CREATE OR REPLACE FUNCTION prevent_equipment_identifier_cross_conflict()
RETURNS trigger AS $$
BEGIN
    IF NEW.asset_code IS NOT NULL
        AND TRIM(NEW.asset_code) <> ''
        AND EXISTS (
            SELECT 1
            FROM equipment e
            WHERE e.id <> COALESCE(NEW.id, -1)
              AND LOWER(TRIM(e.serial_number)) = LOWER(TRIM(NEW.asset_code))
        ) THEN
        RAISE EXCEPTION 'Asset code conflicts with an existing serial number: %', NEW.asset_code;
    END IF;

    IF NEW.serial_number IS NOT NULL
        AND TRIM(NEW.serial_number) <> ''
        AND EXISTS (
            SELECT 1
            FROM equipment e
            WHERE e.id <> COALESCE(NEW.id, -1)
              AND LOWER(TRIM(e.asset_code)) = LOWER(TRIM(NEW.serial_number))
        ) THEN
        RAISE EXCEPTION 'Serial number conflicts with an existing asset code: %', NEW.serial_number;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_equipment_identifier_cross_conflict ON equipment;

CREATE TRIGGER trg_equipment_identifier_cross_conflict
BEFORE INSERT OR UPDATE OF asset_code, serial_number ON equipment
FOR EACH ROW
EXECUTE FUNCTION prevent_equipment_identifier_cross_conflict();
