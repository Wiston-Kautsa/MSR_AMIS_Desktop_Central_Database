CREATE TABLE IF NOT EXISTS maintenance_log (
    id BIGSERIAL PRIMARY KEY,
    asset_code VARCHAR(60) NOT NULL,
    issue TEXT NOT NULL,
    action_taken TEXT,
    performed_by VARCHAR(200),
    maintenance_date DATE NOT NULL DEFAULT CURRENT_DATE,
    cost TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_maintenance_log_status CHECK (status IN ('OPEN', 'COMPLETED'))
);

CREATE INDEX IF NOT EXISTS idx_maintenance_log_asset_code ON maintenance_log(asset_code);
CREATE INDEX IF NOT EXISTS idx_maintenance_log_asset_code_ci ON maintenance_log(LOWER(TRIM(asset_code)));
