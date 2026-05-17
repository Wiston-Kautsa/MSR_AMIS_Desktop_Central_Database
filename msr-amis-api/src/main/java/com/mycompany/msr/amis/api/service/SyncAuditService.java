package com.mycompany.msr.amis.api.service;

import com.mycompany.msr.amis.api.dto.sync.SyncAuditLogResponse;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SyncAuditService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    public SyncAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void log(Long sessionId,
                    String userId,
                    String machineId,
                    String action,
                    String entityType,
                    int recordCount,
                    String status,
                    String errorMessage,
                    Long durationMs) {
        jdbcTemplate.update(
                "INSERT INTO sync_audit_logs(sync_session_id, user_id, machine_id, action, entity_type, record_count, status, error_message, duration_ms) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                sessionId,
                normalize(userId),
                normalize(machineId),
                normalize(action),
                normalize(entityType),
                recordCount,
                normalize(status),
                normalize(errorMessage),
                durationMs
        );
    }

    public List<SyncAuditLogResponse> getLogs(String userId, String machineId, String entityType, String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT a.id, s.session_token, a.user_id, a.machine_id, a.action, a.entity_type, a.record_count, " +
                        "a.status, a.error_message, a.duration_ms, a.created_at " +
                        "FROM sync_audit_logs a LEFT JOIN sync_sessions s ON s.id = a.sync_session_id WHERE 1=1 "
        );
        new FilterBuilder(sql)
                .add("a.user_id", userId)
                .add("a.machine_id", machineId)
                .add("a.entity_type", entityType)
                .add("a.status", status);
        return jdbcTemplate.query(
                sql.append(" ORDER BY a.created_at DESC, a.id DESC").toString(),
                (rs, rowNum) -> new SyncAuditLogResponse(
                        rs.getLong("id"),
                        rs.getString("session_token"),
                        rs.getString("user_id"),
                        rs.getString("machine_id"),
                        rs.getString("action"),
                        rs.getString("entity_type"),
                        rs.getInt("record_count"),
                        rs.getString("status"),
                        rs.getString("error_message"),
                        rs.getObject("duration_ms") == null ? null : rs.getLong("duration_ms"),
                        format(rs.getTimestamp("created_at"))
                )
        );
    }

    public String lastSyncAt(String status) {
        return jdbcTemplate.query(
                "SELECT created_at FROM sync_audit_logs WHERE status=? ORDER BY created_at DESC, id DESC LIMIT 1",
                (rs, rowNum) -> format(rs.getTimestamp("created_at")),
                normalize(status)
        ).stream().findFirst().orElse("");
    }

    private String format(Timestamp timestamp) {
        return timestamp == null ? "" : timestamp.toLocalDateTime().format(FORMATTER);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
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
