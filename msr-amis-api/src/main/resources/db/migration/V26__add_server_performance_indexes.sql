CREATE INDEX IF NOT EXISTS idx_equipment_status_entry_date ON equipment(status, entry_date DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_equipment_entry_date_id ON equipment(entry_date DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_equipment_serial_number_ci ON equipment(LOWER(TRIM(serial_number)));
CREATE INDEX IF NOT EXISTS idx_equipment_asset_code_ci ON equipment(LOWER(TRIM(asset_code)));

CREATE INDEX IF NOT EXISTS idx_users_email_ci ON users(LOWER(TRIM(email)));
CREATE INDEX IF NOT EXISTS idx_users_username_ci ON users(LOWER(TRIM(username)));
CREATE INDEX IF NOT EXISTS idx_users_role_status ON users(role, status);
CREATE INDEX IF NOT EXISTS idx_users_department ON users(department);

CREATE INDEX IF NOT EXISTS idx_assignments_status_date ON assignments(status, date_created DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_assignments_department ON assignments(department);
CREATE INDEX IF NOT EXISTS idx_assignments_equipment_type ON assignments(equipment_type);

CREATE INDEX IF NOT EXISTS idx_distribution_returned_asset ON distribution(returned, asset_code);
CREATE INDEX IF NOT EXISTS idx_distribution_assignment_returned ON distribution(assignment_id, returned);
CREATE INDEX IF NOT EXISTS idx_distribution_assigned_at_id ON distribution(assigned_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_distribution_asset_code_ci ON distribution(LOWER(TRIM(asset_code)));

CREATE INDEX IF NOT EXISTS idx_returns_return_date_id ON returns(return_date DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_returns_asset_code_ci ON returns(LOWER(TRIM(asset_code)));

CREATE INDEX IF NOT EXISTS idx_audit_log_action_time ON audit_log(action_time DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_performed_by_time ON audit_log(performed_by, action_time DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_entity_time ON audit_log(entity, action_time DESC);

CREATE INDEX IF NOT EXISTS idx_password_reset_audit_identifier_time ON password_reset_audit(identifier, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sync_queue_status_created ON sync_queue(status, created_at ASC, id ASC);
CREATE INDEX IF NOT EXISTS idx_sync_queue_status_updated ON sync_queue(status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_sync_queue_machine_status ON sync_queue(machine_id, status);
CREATE INDEX IF NOT EXISTS idx_sync_queue_created_by_status ON sync_queue(LOWER(created_by), status);
CREATE INDEX IF NOT EXISTS idx_sync_audit_logs_status_created ON sync_audit_logs(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sync_audit_logs_machine_created ON sync_audit_logs(machine_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sync_conflicts_entity_status ON sync_conflicts(entity_type, resolution_status);
CREATE INDEX IF NOT EXISTS idx_sync_quarantine_entity_status ON sync_quarantine(entity_type, status);
