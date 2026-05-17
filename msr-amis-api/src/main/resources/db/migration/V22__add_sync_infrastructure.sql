CREATE TABLE IF NOT EXISTS sync_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_token VARCHAR(120) NOT NULL UNIQUE,
    started_by VARCHAR(150) NOT NULL,
    machine_id VARCHAR(120) NOT NULL,
    machine_name VARCHAR(150),
    status VARCHAR(30) NOT NULL DEFAULT 'STARTED',
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMPTZ,
    CONSTRAINT ck_sync_sessions_status CHECK (status IN ('STARTED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS sync_queue (
    id BIGSERIAL PRIMARY KEY,
    sync_session_id BIGINT REFERENCES sync_sessions(id),
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(120),
    operation VARCHAR(30) NOT NULL,
    payload_json JSONB NOT NULL,
    baseline_json JSONB,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(150) NOT NULL,
    machine_id VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    error_message TEXT,
    checksum VARCHAR(128),
    CONSTRAINT ck_sync_queue_operation CHECK (operation IN ('CREATE', 'UPDATE', 'DELETE', 'STATUS', 'MERGE', 'RESTORE')),
    CONSTRAINT ck_sync_queue_status CHECK (status IN ('PENDING', 'PROCESSING', 'APPLIED', 'FAILED', 'REJECTED', 'CONFLICT', 'QUARANTINED')),
    CONSTRAINT uq_sync_queue_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE IF NOT EXISTS sync_audit (
    id BIGSERIAL PRIMARY KEY,
    sync_session_id BIGINT REFERENCES sync_sessions(id),
    user_id VARCHAR(150) NOT NULL,
    machine_id VARCHAR(120) NOT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(50),
    record_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    error_message TEXT,
    duration_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sync_conflicts (
    id BIGSERIAL PRIMARY KEY,
    sync_queue_id BIGINT REFERENCES sync_queue(id),
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(120) NOT NULL,
    local_payload JSONB NOT NULL,
    central_payload JSONB NOT NULL,
    conflict_type VARCHAR(80) NOT NULL,
    resolution_status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    resolution_action VARCHAR(40),
    resolved_by VARCHAR(150),
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sync_conflicts_resolution_status CHECK (resolution_status IN ('OPEN', 'SUBMITTED', 'APPROVED', 'REJECTED', 'RESOLVED')),
    CONSTRAINT ck_sync_conflicts_resolution_action CHECK (resolution_action IS NULL OR resolution_action IN ('KEEP_LOCAL', 'KEEP_CENTRAL', 'MERGE'))
);

CREATE TABLE IF NOT EXISTS sync_lock (
    id BIGSERIAL PRIMARY KEY,
    locked_by VARCHAR(150) NOT NULL,
    machine_id VARCHAR(120) NOT NULL,
    session_token VARCHAR(120),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT ck_sync_lock_status CHECK (status IN ('ACTIVE', 'RELEASED', 'EXPIRED', 'FORCE_RELEASED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sync_lock_active ON sync_lock(status) WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS sync_settings (
    key VARCHAR(120) PRIMARY KEY,
    value TEXT NOT NULL,
    description TEXT,
    editable_by VARCHAR(30) NOT NULL DEFAULT 'SUPER_ADMIN',
    updated_by VARCHAR(150),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sync_quarantine (
    id BIGSERIAL PRIMARY KEY,
    sync_queue_id BIGINT REFERENCES sync_queue(id),
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(120),
    issue TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'QUARANTINED',
    quarantined_by VARCHAR(150),
    quarantined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_by VARCHAR(150),
    released_at TIMESTAMPTZ,
    CONSTRAINT ck_sync_quarantine_status CHECK (status IN ('QUARANTINED', 'RELEASED', 'DISCARDED'))
);

CREATE TABLE IF NOT EXISTS sync_checkpoints (
    id BIGSERIAL PRIMARY KEY,
    sync_session_id BIGINT REFERENCES sync_sessions(id),
    entity_type VARCHAR(50) NOT NULL,
    batch_number INTEGER NOT NULL,
    records_processed INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'STARTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sync_checkpoints_status CHECK (status IN ('STARTED', 'COMPLETED', 'FAILED', 'RESUMABLE'))
);

INSERT INTO sync_settings(key, value, description, editable_by)
VALUES
    ('sync.mode', 'HYBRID', 'Background automatic sync with manual admin controls.', 'SUPER_ADMIN'),
    ('sync.strategy', 'BIDIRECTIONAL_CONTROLLED_CONFLICTS', 'Push local queue to API and pull central state back to desktop.', 'SUPER_ADMIN'),
    ('sync.batch_size', '100', 'Maximum records per sync batch.', 'SUPER_ADMIN'),
    ('sync.max_retries', '3', 'Maximum retry attempts before quarantine.', 'SUPER_ADMIN'),
    ('sync.poll_interval_seconds', '300', 'Background sync interval.', 'SUPER_ADMIN'),
    ('sync.lock_ttl_seconds', '900', 'Sync lock time-to-live before it can be considered expired.', 'SUPER_ADMIN'),
    ('sync.retention_days', '90', 'Audit and completed queue retention target.', 'SUPER_ADMIN')
ON CONFLICT (key) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_sync_queue_status ON sync_queue(status);
CREATE INDEX IF NOT EXISTS idx_sync_queue_entity ON sync_queue(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_sync_queue_session ON sync_queue(sync_session_id);
CREATE INDEX IF NOT EXISTS idx_sync_audit_session ON sync_audit(sync_session_id);
CREATE INDEX IF NOT EXISTS idx_sync_conflicts_status ON sync_conflicts(resolution_status);
CREATE INDEX IF NOT EXISTS idx_sync_quarantine_status ON sync_quarantine(status);
