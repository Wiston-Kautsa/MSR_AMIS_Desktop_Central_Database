package com.mycompany.msr.amis.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.msr.amis.api.dto.sync.SyncQueueRecordResponse;
import com.mycompany.msr.amis.api.dto.sync.SyncQueueRecordRequest;
import com.mycompany.msr.amis.api.exception.ApiException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SyncQueueService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    public SyncQueueService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long enqueue(Long sessionId, String actor, String machineId, SyncQueueRecordRequest record) {
        try {
            return jdbcTemplate.queryForObject(
                    "INSERT INTO sync_queue(sync_session_id, entity_type, entity_id, operation, payload_json, baseline_json, created_by, machine_id, idempotency_key, checksum) " +
                            "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?) RETURNING id",
                    Long.class,
                    sessionId,
                    normalize(record.entityType()),
                    normalize(record.entityId()),
                    normalize(record.operation()),
                    serialize(record.payload()),
                    serialize(record.baseline()),
                    normalize(actor),
                    normalize(machineId),
                    normalizeRequired(record.idempotencyKey(), "Idempotency key is required."),
                    normalize(record.checksum())
            );
        } catch (DuplicateKeyException duplicateKeyException) {
            return findByIdempotencyKey(record.idempotencyKey());
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unable to enqueue sync record: " + exception.getMessage());
        }
    }

    public boolean alreadyProcessed(String idempotencyKey) {
        String normalizedKey = normalize(idempotencyKey);
        if (normalizedKey.isBlank()) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sync_queue WHERE idempotency_key=? AND status='APPLIED'",
                Long.class,
                normalizedKey
        );
        return count != null && count > 0;
    }

    public void markIdempotencyKeyProcessed(String actor,
                                            String machineId,
                                            String idempotencyKey,
                                            String entityType,
                                            String entityId,
                                            String operation,
                                            Object payload) {
        String normalizedKey = normalizeRequired(idempotencyKey, "Idempotency key is required.");
        try {
            jdbcTemplate.update(
                    "INSERT INTO sync_queue(entity_type, entity_id, operation, payload_json, status, created_by, machine_id, idempotency_key, updated_at) " +
                            "VALUES (?, ?, ?, ?::jsonb, 'APPLIED', ?, ?, ?, CURRENT_TIMESTAMP) " +
                            "ON CONFLICT(idempotency_key) DO UPDATE SET status='APPLIED', error_message=NULL, updated_at=CURRENT_TIMESTAMP",
                    normalize(entityType),
                    normalize(entityId),
                    normalize(operation),
                    serialize(payload),
                    normalize(actor),
                    normalize(machineId),
                    normalizedKey
            );
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unable to record idempotency key: " + exception.getMessage());
        }
    }

    public void markApplied(long queueId) {
        updateStatus(queueId, "APPLIED", "");
    }

    public void markFailed(long queueId, String errorMessage) {
        updateStatus(queueId, "FAILED", errorMessage);
    }

    public void markConflict(long queueId, String errorMessage) {
        updateStatus(queueId, "CONFLICT", errorMessage);
    }

    public void markQuarantined(long queueId, String errorMessage) {
        updateStatus(queueId, "QUARANTINED", errorMessage);
    }

    public int retryFailed(List<Long> queueIds, String actor, boolean superAdmin) {
        if (queueIds == null || queueIds.isEmpty()) {
            return jdbcTemplate.update(
                    superAdmin
                            ? "UPDATE sync_queue SET status='PENDING', retry_count=retry_count+1, error_message=NULL, updated_at=CURRENT_TIMESTAMP WHERE status IN ('FAILED', 'REJECTED', 'CONFLICT', 'QUARANTINED')"
                            : "UPDATE sync_queue SET status='PENDING', retry_count=retry_count+1, error_message=NULL, updated_at=CURRENT_TIMESTAMP WHERE status IN ('FAILED', 'REJECTED', 'CONFLICT', 'QUARANTINED') AND LOWER(created_by)=LOWER(?)",
                    superAdmin ? new Object[]{} : new Object[]{normalize(actor)}
            );
        }
        int updated = 0;
        for (Long queueId : queueIds) {
            updated += jdbcTemplate.update(
                    superAdmin
                            ? "UPDATE sync_queue SET status='PENDING', retry_count=retry_count+1, error_message=NULL, updated_at=CURRENT_TIMESTAMP WHERE id=? AND status IN ('FAILED', 'REJECTED', 'CONFLICT', 'QUARANTINED')"
                            : "UPDATE sync_queue SET status='PENDING', retry_count=retry_count+1, error_message=NULL, updated_at=CURRENT_TIMESTAMP WHERE id=? AND status IN ('FAILED', 'REJECTED', 'CONFLICT', 'QUARANTINED') AND LOWER(created_by)=LOWER(?)",
                    superAdmin ? new Object[]{queueId} : new Object[]{queueId, normalize(actor)}
            );
        }
        return updated;
    }

    public int clearCompletedLogs() {
        jdbcTemplate.update(
                "DELETE FROM sync_conflicts WHERE sync_queue_id IN (SELECT id FROM sync_queue WHERE status='APPLIED')"
        );
        jdbcTemplate.update(
                "DELETE FROM sync_quarantine WHERE sync_queue_id IN (SELECT id FROM sync_queue WHERE status='APPLIED')"
        );
        int queueRows = jdbcTemplate.update("DELETE FROM sync_queue WHERE status='APPLIED'");
        int auditRows = jdbcTemplate.update("DELETE FROM sync_audit_logs WHERE status='SUCCESS'");
        return queueRows + auditRows;
    }

    public int clearQueue() {
        int conflicts = jdbcTemplate.update("DELETE FROM sync_conflicts");
        int quarantine = jdbcTemplate.update("DELETE FROM sync_quarantine");
        int queueRows = jdbcTemplate.update("DELETE FROM sync_queue");
        return conflicts + quarantine + queueRows;
    }

    public int resetSyncState() {
        int conflicts = jdbcTemplate.update("DELETE FROM sync_conflicts");
        int quarantine = jdbcTemplate.update("DELETE FROM sync_quarantine");
        int checkpoints = jdbcTemplate.update("DELETE FROM sync_checkpoints");
        int queueRows = jdbcTemplate.update("DELETE FROM sync_queue");
        int auditRows = jdbcTemplate.update("DELETE FROM sync_audit_logs");
        int lockRows = jdbcTemplate.update("DELETE FROM sync_locks");
        int sessionRows = jdbcTemplate.update("DELETE FROM sync_sessions");
        return conflicts + quarantine + checkpoints + queueRows + auditRows + lockRows + sessionRows;
    }

    public long countByStatus(String status) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sync_queue WHERE status=?", Long.class, normalize(status));
        return count == null ? 0L : count;
    }

    public List<SyncQueueRecordResponse> getQueueRecords(String entityType, String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, entity_type, operation, entity_id, created_by, status, retry_count, machine_id, " +
                        "idempotency_key, error_message, created_at, updated_at FROM sync_queue WHERE 1=1 "
        );
        new FilterBuilder(sql)
                .add("entity_type", entityType)
                .add("status", status);
        return jdbcTemplate.query(
                sql.append(" ORDER BY created_at ASC, id ASC").toString(),
                (rs, rowNum) -> new SyncQueueRecordResponse(
                        rs.getLong("id"),
                        rs.getString("entity_type"),
                        rs.getString("operation"),
                        rs.getString("entity_id"),
                        rs.getString("created_by"),
                        rs.getString("status"),
                        rs.getInt("retry_count"),
                        rs.getString("machine_id"),
                        rs.getString("idempotency_key"),
                        buildDescription(rs.getString("entity_type"), rs.getString("operation"), rs.getString("entity_id")),
                        rs.getString("error_message"),
                        format(rs.getTimestamp("created_at")),
                        format(rs.getTimestamp("updated_at"))
                )
        );
    }

    public List<Long> pendingIdsForSession(Long sessionId) {
        return jdbcTemplate.query(
                "SELECT id FROM sync_queue WHERE sync_session_id=? AND status='PENDING' ORDER BY " +
                        "CASE entity_type WHEN 'USER' THEN 10 WHEN 'DEPARTMENT' THEN 20 WHEN 'EQUIPMENT' THEN 30 " +
                        "WHEN 'ASSIGNMENT' THEN 40 WHEN 'DISTRIBUTION' THEN 50 WHEN 'RETURN' THEN 60 ELSE 90 END, id",
                (rs, rowNum) -> rs.getLong("id"),
                sessionId
        );
    }

    private long findByIdempotencyKey(String idempotencyKey) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sync_queue WHERE idempotency_key=?",
                Long.class,
                normalize(idempotencyKey)
        );
    }

    private void updateStatus(long queueId, String status, String errorMessage) {
        jdbcTemplate.update(
                "UPDATE sync_queue SET status=?, error_message=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
                normalize(status),
                normalize(errorMessage),
                queueId
        );
    }

    private String serialize(Object value) throws Exception {
        return value == null ? "{}" : OBJECT_MAPPER.writeValueAsString(value);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildDescription(String entityType, String operation, String entityId) {
        return normalize(operation) + " " + normalize(entityType) + (normalize(entityId).isBlank() ? "" : " " + normalize(entityId));
    }

    private String format(Timestamp timestamp) {
        return timestamp == null ? "" : timestamp.toLocalDateTime().format(FORMATTER);
    }

    private static final class FilterBuilder {
        private final StringBuilder sql;

        private FilterBuilder(StringBuilder sql) {
            this.sql = sql;
        }

        private FilterBuilder add(String column, String value) {
            if (value != null && !value.isBlank()) {
                sql.append(" AND LOWER(").append(column).append(") = LOWER('")
                        .append(value.trim().replace("'", "''")).append("') ");
            }
            return this;
        }
    }
}
