CREATE UNIQUE INDEX IF NOT EXISTS ux_distribution_active_asset_code_ci
    ON distribution (LOWER(TRIM(asset_code)))
    WHERE returned = FALSE;
