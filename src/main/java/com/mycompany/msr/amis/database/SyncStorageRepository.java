package com.mycompany.msr.amis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
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
                            "description TEXT, " +
                            "error_message TEXT, " +
                            "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
                            "processed_at TEXT" +
                    ")"
            );
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
        String sql = "INSERT INTO sync_queue (entity_type, operation_type, entity_key, payload, baseline_snapshot, actor, status, description) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, normalize(entityType));
            ps.setString(2, normalize(operationType));
            ps.setString(3, normalize(entityKey));
            ps.setString(4, serialize(payload));
            ps.setString(5, serializeNullable(baselineSnapshot));
            ps.setString(6, normalizeActor(actor));
            ps.setString(7, normalize(description));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
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
                        rs.getString("description"),
                        rs.getString("error_message"),
                        rs.getString("created_at"),
                        rs.getString("processed_at")
                ));
            }
        }
        return items;
    }

    public List<SyncAuditRecord> loadAuditRecords(Connection connection) throws Exception {
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

    public static final class StoredSyncQueueItem {
        private final long id;
        private final String entityType;
        private final String operationType;
        private final String entityKey;
        private final JsonNode payload;
        private final JsonNode baselineSnapshot;
        private final String actor;
        private final String status;
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
