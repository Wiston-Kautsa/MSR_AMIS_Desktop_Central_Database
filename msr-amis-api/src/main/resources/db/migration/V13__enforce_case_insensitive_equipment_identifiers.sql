CREATE UNIQUE INDEX IF NOT EXISTS ux_equipment_asset_code_ci
    ON equipment (LOWER(TRIM(asset_code)));

CREATE UNIQUE INDEX IF NOT EXISTS ux_equipment_serial_number_ci
    ON equipment (LOWER(TRIM(serial_number)));
