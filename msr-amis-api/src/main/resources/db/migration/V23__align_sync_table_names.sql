ALTER TABLE IF EXISTS sync_audit RENAME TO sync_audit_logs;
ALTER TABLE IF EXISTS sync_lock RENAME TO sync_locks;

ALTER INDEX IF EXISTS idx_sync_audit_session RENAME TO idx_sync_audit_logs_session;
ALTER INDEX IF EXISTS uq_sync_lock_active RENAME TO uq_sync_locks_active;

CREATE INDEX IF NOT EXISTS idx_sync_audit_logs_session ON sync_audit_logs(sync_session_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_sync_locks_active ON sync_locks(status) WHERE status = 'ACTIVE';
