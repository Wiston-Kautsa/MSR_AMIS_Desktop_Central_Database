package com.mycompany.msr.amis.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.msr.amis.api.dto.CommonMessageResponse;
import com.mycompany.msr.amis.api.dto.sync.SyncConflictResolveRequest;
import com.mycompany.msr.amis.api.dto.sync.SyncConflictResponse;
import com.mycompany.msr.amis.api.exception.ApiException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SyncConflictService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    public SyncConflictService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long createConflict(long queueId,
                               String entityType,
                               String entityId,
                               JsonNode localPayload,
                               JsonNode centralPayload,
                               String conflictType) {
        try {
            Long id = jdbcTemplate.queryForObject(
                    "INSERT INTO sync_conflicts(sync_queue_id, entity_type, entity_id, local_payload, central_payload, conflict_type) " +
                            "VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?) RETURNING id",
                    Long.class,
                    queueId,
                    normalize(entityType),
                    normalize(entityId),
                    serialize(localPayload),
                    serialize(centralPayload),
                    normalize(conflictType)
            );
            return id == null ? 0L : id;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unable to create sync conflict: " + exception.getMessage());
        }
    }

    public List<SyncConflictResponse> getConflicts(String entityType, String status) {
        String sql = "SELECT * FROM sync_conflicts WHERE (?='' OR LOWER(entity_type)=LOWER(?)) " +
                "AND (?='' OR LOWER(resolution_status)=LOWER(?)) ORDER BY created_at DESC, id DESC";
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new SyncConflictResponse(
                        rs.getLong("id"),
                        rs.getString("entity_type"),
                        rs.getString("entity_id"),
                        readJson(rs.getString("local_payload")),
                        readJson(rs.getString("central_payload")),
                        rs.getString("conflict_type"),
                        rs.getString("resolution_status"),
                        rs.getString("resolution_action"),
                        rs.getString("resolved_by"),
                        format(rs.getTimestamp("resolved_at")),
                        format(rs.getTimestamp("created_at"))
                ),
                normalize(entityType),
                normalize(entityType),
                normalize(status),
                normalize(status)
        );
    }

    public CommonMessageResponse resolve(String actor, SyncConflictResolveRequest request) {
        String action = normalize(request.resolutionAction()).toUpperCase();
        if (!List.of("KEEP_LOCAL", "KEEP_CENTRAL", "MERGE").contains(action)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported conflict resolution action.");
        }
        int updated = jdbcTemplate.update(
                "UPDATE sync_conflicts SET resolution_status='RESOLVED', resolution_action=?, resolved_by=?, resolved_at=CURRENT_TIMESTAMP WHERE id=? AND resolution_status IN ('OPEN', 'SUBMITTED', 'APPROVED')",
                action,
                normalize(actor),
                request.conflictId()
        );
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Open sync conflict was not found.");
        }
        return new CommonMessageResponse(true, "Conflict resolved with action " + action + ".");
    }

    public long openConflictCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sync_conflicts WHERE resolution_status IN ('OPEN', 'SUBMITTED', 'APPROVED')",
                Long.class
        );
        return count == null ? 0L : count;
    }

    private JsonNode readJson(String value) {
        try {
            return value == null || value.isBlank() ? OBJECT_MAPPER.createObjectNode() : OBJECT_MAPPER.readTree(value);
        } catch (Exception exception) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private String serialize(JsonNode value) throws Exception {
        return value == null ? "{}" : OBJECT_MAPPER.writeValueAsString(value);
    }

    private String format(Timestamp timestamp) {
        return timestamp == null ? "" : timestamp.toLocalDateTime().format(FORMATTER);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
