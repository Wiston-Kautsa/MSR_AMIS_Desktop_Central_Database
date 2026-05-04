CREATE TEMP TABLE tmp_asset_code_mapping AS
SELECT
    id,
    asset_code AS old_asset_code,
    'MSR-' ||
    SUBSTRING(
        CASE
            WHEN LENGTH(REPLACE(TRIM(UPPER(COALESCE(category, 'OTH'))), ' ', '')) >= 3
                THEN REPLACE(TRIM(UPPER(COALESCE(category, 'OTH'))), ' ', '')
            ELSE REPLACE(TRIM(UPPER(COALESCE(category, 'OTH'))), ' ', '') || 'OTH'
        END
        FROM 1 FOR 3
    ) ||
    '-' ||
    LPAD(id::text, 3, '0') AS new_asset_code
FROM equipment;

UPDATE distribution d
SET asset_code = m.new_asset_code
FROM tmp_asset_code_mapping m
WHERE LOWER(TRIM(d.asset_code)) = LOWER(TRIM(m.old_asset_code))
  AND LOWER(TRIM(m.old_asset_code)) <> LOWER(TRIM(m.new_asset_code));

UPDATE returns r
SET asset_code = m.new_asset_code
FROM tmp_asset_code_mapping m
WHERE LOWER(TRIM(r.asset_code)) = LOWER(TRIM(m.old_asset_code))
  AND LOWER(TRIM(m.old_asset_code)) <> LOWER(TRIM(m.new_asset_code));

UPDATE audit_log a
SET entity_id = m.new_asset_code
FROM tmp_asset_code_mapping m
WHERE LOWER(COALESCE(TRIM(a.entity_id), '')) = LOWER(TRIM(m.old_asset_code))
  AND LOWER(TRIM(m.old_asset_code)) <> LOWER(TRIM(m.new_asset_code));

UPDATE audit_log a
SET details = REPLACE(a.details, m.old_asset_code, m.new_asset_code)
FROM tmp_asset_code_mapping m
WHERE a.details IS NOT NULL
  AND POSITION(m.old_asset_code IN a.details) > 0
  AND LOWER(TRIM(m.old_asset_code)) <> LOWER(TRIM(m.new_asset_code));

UPDATE equipment e
SET asset_code = m.new_asset_code,
    updated_at = CURRENT_TIMESTAMP
FROM tmp_asset_code_mapping m
WHERE e.id = m.id
  AND LOWER(TRIM(m.old_asset_code)) <> LOWER(TRIM(m.new_asset_code));

DROP TABLE tmp_asset_code_mapping;
