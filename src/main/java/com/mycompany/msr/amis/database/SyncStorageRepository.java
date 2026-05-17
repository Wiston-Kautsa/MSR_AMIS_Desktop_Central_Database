package com.mycompany.msr.amis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public final class SyncStorageRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    public void ensureSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS sync_queue (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "entity_type TEXT NOT NULL, " +
                            "operation_type TEXT NOT NULL, " +
                            "entity_key TEXT, " +
                            "payload TEXT NOT NULL, " +
                            "baseline_snapshot TEXT, " +
                            "actor TEXT NOT NULL, " +
                            "status TEXT NOT NULL DEFAULT 'PENDING', " +
                            "retry_count INTEGER NOT NULL DEFAULT 0, " +
                            "machine_id TEXT, " +
                            "idempotency_key TEXT, " +
                            "description TEXT, " +
                            "error_message TEXT, " +
                            "quarantine_reason TEXT, " +
                            "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
                            "processed_at TEXT" +
                    ")"
            );
            addColumnIfMissing(connection, "sync_queue", "retry_count", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "sync_queue", "machine_id", "TEXT");
            addColumnIfMissing(connection, "sync_queue", "idempotency_key", "TEXT");
            addColumnIfMissing(connection, "sync_queue", "quarantine_reason", "TEXT");
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_sync_queue_idempotency_key ON sync_queue(idempotency_key)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_sync_queue_status ON sync_queue(status)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_sync_queue_machine ON sync_queue(machine_id)");
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS sync_audit (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "queue_id INTEGER, " +
                            "actor TEXT NOT NULL, " +
                            "action TEXT NOT NULL, " +
                            "outcome TEXT NOT NULL, " +
                            "details TEXT, " +
                            "created_at TEXT DEFAULT CURRENT_TIMESTAMP" +
                    ")"
            );
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS sync_lock (" +
                            "id INTEGER PRIMARY KEY CHECK(id = 1), " +
                            "locked_by TEXT NOT NULL, " +
                            "session_id TEXT NOT NULL, " +
                            "started_at TEXT DEFAULT CURRENT_TIMESTAMP" +
                    ")"
            );
        }
    }

    public long insertQueueRecord(Connection connection,
                                  String entityType,
                                  String operationType,
                                  String entityKey,
                                  Object payload,
                                  Object baselineSnapshot,
                                  String actor,
                                  String description) throws Exception {
        String normalizedEntityType = normalize(entityType);
        String normalizedOperationType = normalize(operationType);
        String normalizedEntityKey = normalize(entityKey);
        String normalizedActor = normalizeActor(actor);
        String machineId = machineId();
        String idempotencyKey = idempotencyKey(machineId, normalizedEntityType, normalizedOperationType, normalizedEntityKey, payload);
        String sql = "INSERT OR IGNORE INTO sync_queue (entity_type, operation_type, entity_key, payload, baseline_snapshot, actor, status, retry_count, machine_id, idempotency_key, description) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, normalizedEntityType);
            ps.setString(2, normalizedOperationType);
            ps.setString(3, normalizedEntityKey);
            ps.setString(4, serialize(payload));
            ps.setString(5, serializeNullable(baselineSnapshot));
            ps.setString(6, normalizedActor);
            ps.setString(7, machineId);
            ps.setString(8, idempotencyKey);
            ps.setString(9, normalize(description));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM sync_queue WHERE idempotency_key=?")) {
            ps.setString(1, idempotencyKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("id") : 0L;
            }
        }
    }

    public void insertAuditRecord(Connection connection,
                                  long queueId,
                                  String actor,
                                  String action,
                                  String outcome,
                                  String details) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO sync_audit (queue_id, actor, action, outcome, details) VALUES (?, ?, ?, ?, ?)"
        )) {
            ps.setLong(1, queueId);
            ps.setString(2, normalizeActor(actor));
            ps.setString(3, normalize(action));
            ps.setString(4, normalize(outcome));
            ps.setString(5, normalize(details));
            ps.executeUpdate();
        }
    }

    public List<StoredSyncQueueItem> loadQueueItems(Connection connection) throws Exception {
        ensureSchema(connection);
        List<StoredSyncQueueItem> items = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM sync_queue ORDER BY created_at ASC, id ASC"
        ); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                items.add(new StoredSyncQueueItem(
                        rs.getLong("id"),
                        rs.getString("entity_type"),
                        rs.getString("operation_type"),
                        rs.getString("entity_key"),
                        readJson(rs.getString("payload")),
                        readJsonNullable(rs.getString("baseline_snapshot")),
                        rs.getString("actor"),
                        rs.getString("status"),
                        rs.getInt("retry_count"),
                        rs.getString("machine_id"),
                        rs.getString("idempotency_key"),
                        rs.getString("description"),
                        firstNonBlank(rs.getString("error_message"), rs.getString("quarantine_reason")),
                        rs.getString("created_at"),
                        rs.getString("processed_at")
                ));
            }
        }
        return items;
    }

    public List<SyncAuditRecord> loadAuditRecords(Connection connection) throws Exception {
        ensureSchema(connection);
        List<SyncAuditRecord> records = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM sync_audit ORDER BY created_at DESC, id DESC"
        ); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                records.add(new SyncAuditRecord(
                        rs.getLong("id"),
                        rs.getLong("queue_id"),
                        rs.getString("actor"),
                        rs.getString("action"),
                        rs.getString("outcome"),
                        rs.getString("details"),
                        rs.getString("created_at")
                ));
            }
        }
        return records;
    }

    public void markApplied(Connection connection, long queueId, String details) throws Exception {
        updateStatus(connection, queueId, "APPLIED", null, details);
    }

    public void markRejected(Connection connection, long queueId, String errorMessage) throws Exception {
        updateStatus(connection, queueId, "REJECTED", errorMessage, null);
    }

    public void markFailed(Connection connection, long queueId, String errorMessage) throws Exception {
        updateStatus(connection, queueId, "FAILED", errorMessage, null);
    }

    public void markQuarantined(Connection connection, long queueId, String reason) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE sync_queue SET status='QUARANTINED', error_message=?, quarantine_reason=?, processed_at=CURRENT_TIMESTAMP WHERE id=?"
        )) {
            ps.setString(1, reason == null ? null : reason.trim());
            ps.setString(2, reason == null ? null : reason.trim());
            ps.setLong(3, queueId);
            ps.executeUpdate();
        }
    }

    public int retryRejectedAndFailed(Connection connection, String actor) throws Exception {
        int updated;
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE sync_queue SET status='PENDING', retry_count=retry_count+1, error_message=NULL, processed_at=NULL " +
                        "WHERE status IN ('REJECTED', 'FAILED')"
        )) {
            updated = ps.executeUpdate();
        }
        if (updated > 0) {
            insertAuditRecord(connection, 0, actor, "SYNC_RETRY_REQUESTED", "PENDING",
                    "Rejected and failed queue records moved back to pending. Count: " + updated + ".");
        }
        return updated;
    }

    public int clearCompleted(Connection connection, String actor) throws Exception {
        int queueRows;
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM sync_queue WHERE status='APPLIED'"
        )) {
            queueRows = ps.executeUpdate();
        }
        int auditRows;
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM sync_audit WHERE outcome='APPLIED'"
        )) {
            auditRows = ps.executeUpdate();
        }
        insertAuditRecord(connection, 0, actor, "SYNC_COMPLETED_LOGS_CLEARED", "CLEARED",
                "Completed sync records cleared. Queue: " + queueRows + ". Audit: " + auditRows + ".");
        return queueRows + auditRows;
    }

    public int clearQueue(Connection connection, String actor) throws Exception {
        int queueRows;
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM sync_queue")) {
            queueRows = ps.executeUpdate();
        }
        insertAuditRecord(connection, 0, actor, "SYNC_QUEUE_CLEARED", "CLEARED",
                "All sync queue records cleared. Count: " + queueRows + ".");
        return queueRows;
    }

    public int resetSyncState(Connection connection, String actor) throws Exception {
        int queueRows;
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM sync_queue")) {
            queueRows = ps.executeUpdate();
        }
        int auditRows;
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM sync_audit")) {
            auditRows = ps.executeUpdate();
        }
        insertAuditRecord(connection, 0, actor, "SYNC_STATE_RESET", "RESET",
                "Sync queue and audit state reset. Queue: " + queueRows + ". Audit: " + auditRows + ".");
        return queueRows + auditRows;
    }

    public boolean tryAcquireLock(Connection connection, String actor, String sessionId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO sync_lock (id, locked_by, session_id) VALUES (1, ?, ?)"
        )) {
            ps.setString(1, normalizeActor(actor));
            ps.setString(2, normalize(sessionId));
            return ps.executeUpdate() > 0;
        }
    }

    public void releaseLock(Connection connection, String sessionId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM sync_lock WHERE id=1 AND session_id=?"
        )) {
            ps.setString(1, normalize(sessionId));
            ps.executeUpdate();
        }
    }

    public void forceReleaseLock(Connection connection, String actor) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM sync_lock WHERE id=1")) {
            ps.executeUpdate();
        }
        insertAuditRecord(connection, 0, actor, "SYNC_LOCK_FORCE_RELEASED", "RELEASED",
                "Sync lock was force released by " + normalizeActor(actor) + ".");
    }

    public SyncLockInfo loadLockInfo(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT locked_by, session_id, started_at FROM sync_lock WHERE id=1"
        ); ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return new SyncLockInfo(false, "", "", "");
            }
            return new SyncLockInfo(
                    true,
                    rs.getString("locked_by"),
                    rs.getString("started_at"),
                    rs.getString("session_id")
            );
        }
    }

    private void updateStatus(Connection connection, long queueId, String status, String errorMessage, String details) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE sync_queue SET status=?, error_message=?, processed_at=CURRENT_TIMESTAMP WHERE id=?"
        )) {
            ps.setString(1, status);
            ps.setString(2, errorMessage == null ? null : errorMessage.trim());
            ps.setLong(3, queueId);
            ps.executeUpdate();
        }
    }

    private void addColumnIfMissing(Connection connection, String table, String column, String definition) throws Exception {
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, table, column)) {
            if (rs.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private String serialize(Object value) throws Exception {
        return OBJECT_MAPPER.writeValueAsString(value);
    }

    private String serializeNullable(Object value) throws Exception {
        return value == null ? null : serialize(value);
    }

    private JsonNode readJson(String raw) throws Exception {
        return OBJECT_MAPPER.readTree(raw);
    }

    private JsonNode readJsonNullable(String raw) throws Exception {
        return raw == null || raw.isBlank() ? null : OBJECT_MAPPER.readTree(raw);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeActor(String actor) {
        String normalized = normalize(actor);
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = normalize(first);
        return normalizedFirst.isBlank() ? normalize(second) : normalizedFirst;
    }

    private String machineId() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            if (host != null && !host.isBlank()) {
                return host.trim();
            }
        } catch (Exception ignored) {
            // Fall back to environment name or a stable placeholder.
        }
        String computerName = System.getenv("COMPUTERNAME");
        return computerName == null || computerName.isBlank() ? "UNKNOWN_MACHINE" : computerName.trim();
    }

    private String idempotencyKey(String machineId, String entityType, String operationType, String entityKey, Object payload) throws Exception {
        String base = normalize(machineId) + "|" + normalize(entityType) + "|" + normalize(operationType) + "|"
                + normalize(entityKey) + "|" + serialize(payload);
        return UUID.nameUUIDFromBytes(base.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    public static final class StoredSyncQueueItem {
        private final long id;
        private final String entityType;
        private final String operationType;
        private final String entityKey;
        private final JsonNode payload;
        private final JsonNode baselineSnapshot;
        private final String actor;
        private final String status;
        private final int retryCount;
        private final String machineId;
        private final String idempotencyKey;
        private final String description;
        private final String errorMessage;
        private final String createdAt;
        private final String processedAt;

        public StoredSyncQueueItem(long id,
                                   String entityType,
                                   String operationType,
                                   String entityKey,
                                   JsonNode payload,
                                   JsonNode baselineSnapshot,
                                   String actor,
                                   String status,
                                   int retryCount,
                                   String machineId,
                                   String idempotencyKey,
                                   String description,
                                   String errorMessage,
                                   String createdAt,
                                   String processedAt) {
            this.id = id;
            this.entityType = entityType;
            this.operationType = operationType;
            this.entityKey = entityKey;
            this.payload = payload;
            this.baselineSnapshot = baselineSnapshot;
            this.actor = actor;
            this.status = status;
            this.retryCount = retryCount;
            this.machineId = machineId;
            this.idempotencyKey = idempotencyKey;
            this.description = description;
            this.errorMessage = errorMessage;
            this.createdAt = createdAt;
            this.processedAt = processedAt;
        }

        public long getId() {
            return id;
        }

        public String getEntityType() {
            return entityType;
        }

        public String getOperationType() {
            return operationType;
        }

        public String getEntityKey() {
            return entityKey;
        }

        public JsonNode getPayload() {
            return payload;
        }

        public JsonNode getBaselineSnapshot() {
            return baselineSnapshot;
        }

        public String getActor() {
            return actor;
        }

        public String getStatus() {
            return status;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public String getMachineId() {
            return machineId;
        }

        public String getIdempotencyKey() {
            return idempotencyKey;
        }

        public String getDescription() {
            return description;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public String getProcessedAt() {
            return processedAt;
        }
    }
}
