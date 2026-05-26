package com.mycompany.msr.amis.api.service;

import com.mycompany.msr.amis.api.dto.CommonMessageResponse;
import com.mycompany.msr.amis.api.dto.asset.AssetHistoryRecordResponse;
import com.mycompany.msr.amis.api.dto.asset.AssetHistoryResponse;
import com.mycompany.msr.amis.api.dto.asset.AssetHistorySummaryResponse;
import com.mycompany.msr.amis.api.dto.audit.AuditLogRequest;
import com.mycompany.msr.amis.api.dto.audit.AuditLogResponse;
import com.mycompany.msr.amis.api.exception.ApiException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import com.mycompany.msr.amis.api.domain.UserAccount;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditHistoryService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final UserManagementService userManagementService;

    public AuditHistoryService(JdbcTemplate jdbcTemplate, UserManagementService userManagementService) {
        this.jdbcTemplate = jdbcTemplate;
        this.userManagementService = userManagementService;
    }

    public AssetHistoryResponse getAssetHistory(String assetCode) {
        List<AssetHistorySummaryResponse> summaries = jdbcTemplate.query(
                "SELECT asset_code, serial_number, name, category, entry_date, status " +
                        "FROM equipment WHERE LOWER(TRIM(asset_code)) = LOWER(TRIM(?))",
                (rs, rowNum) -> new AssetHistorySummaryResponse(
                        rs.getString("asset_code"),
                        rs.getString("serial_number"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getDate("entry_date").toLocalDate().toString(),
                        rs.getString("status")
                ),
                assetCode
        );
        if (summaries.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Asset not found.");
        }

        List<AssetHistoryRecordResponse> records = jdbcTemplate.query(
                "SELECT activity_date, event_type, actor, affected_person, details, status " +
                        "FROM (" +
                        "    SELECT e.entry_date AS activity_date, " +
                        "           'REGISTERED' AS event_type, " +
                        "           COALESCE(e.source, 'System') AS actor, " +
                        "           '' AS affected_person, " +
                        "           'Asset added: ' || COALESCE(e.name, '') || " +
                        "           CASE WHEN COALESCE(TRIM(e.category), '') = '' THEN '' ELSE ' | Category: ' || TRIM(e.category) END || " +
                        "           CASE WHEN COALESCE(TRIM(e.item_condition), '') = '' THEN '' ELSE ' | Condition: ' || TRIM(e.item_condition) END || " +
                        "           CASE WHEN COALESCE(TRIM(e.serial_number), '') = '' THEN '' ELSE ' | Serial: ' || TRIM(e.serial_number) END AS details, " +
                        "           COALESCE(e.status, '') AS status, " +
                        "           0 AS event_order, " +
                        "           e.id AS record_id " +
                        "    FROM equipment e WHERE LOWER(TRIM(e.asset_code)) = LOWER(TRIM(?)) " +
                        "    UNION ALL " +
                        "    SELECT d.assigned_at AS activity_date, " +
                        "           'ISSUED' AS event_type, " +
                        "           COALESCE(a.person, '') AS actor, " +
                        "           COALESCE(d.assigned_to, '') AS affected_person, " +
                        "           CASE WHEN COALESCE(TRIM(a.reason), '') = '' THEN 'Asset issued' ELSE 'Reason: ' || TRIM(a.reason) END || " +
                        "           CASE WHEN COALESCE(TRIM(a.department), '') = '' THEN '' ELSE ' | Department: ' || TRIM(a.department) END || " +
                        "           CASE WHEN COALESCE(TRIM(a.equipment_type), '') = '' THEN '' ELSE ' | Equipment type: ' || TRIM(a.equipment_type) END || " +
                        "           CASE WHEN COALESCE(TRIM(d.phone), '') = '' THEN '' ELSE ' | Phone: ' || TRIM(d.phone) END || " +
                        "           CASE WHEN COALESCE(TRIM(d.nid), '') = '' THEN '' ELSE ' | NID: ' || TRIM(d.nid) END AS details, " +
                        "           CASE WHEN d.returned THEN 'RETURNED' ELSE 'ASSIGNED' END AS status, " +
                        "           1 AS event_order, d.id AS record_id " +
                        "    FROM distribution d LEFT JOIN assignments a ON a.id = d.assignment_id " +
                        "    WHERE LOWER(TRIM(d.asset_code)) = LOWER(TRIM(?)) " +
                        "    UNION ALL " +
                        "    SELECT m.maintenance_date AS activity_date, " +
                        "           CASE WHEN UPPER(COALESCE(m.status, '')) = 'COMPLETED' THEN 'MAINTENANCE COMPLETED' ELSE 'MAINTENANCE' END AS event_type, " +
                        "           COALESCE(m.performed_by, '') AS actor, " +
                        "           '' AS affected_person, " +
                        "           'Issue: ' || COALESCE(m.issue, '') || " +
                        "           CASE WHEN COALESCE(TRIM(m.action_taken), '') = '' THEN '' ELSE ' | Action taken: ' || TRIM(m.action_taken) END || " +
                        "           CASE WHEN COALESCE(TRIM(m.cost), '') = '' THEN '' ELSE ' | Cost: ' || TRIM(m.cost) END AS details, " +
                        "           COALESCE(m.status, '') AS status, " +
                        "           3 AS event_order, m.id AS record_id " +
                        "    FROM maintenance_log m WHERE LOWER(TRIM(m.asset_code)) = LOWER(TRIM(?)) " +
                        "    UNION ALL " +
                        "    SELECT r.return_date AS activity_date, " +
                        "           'RETURNED' AS event_type, " +
                        "           COALESCE(r.returned_by, '') AS actor, " +
                        "           '' AS affected_person, " +
                        "           CASE WHEN COALESCE(TRIM(r.item_condition), '') = '' THEN 'Asset returned' ELSE 'Condition: ' || TRIM(r.item_condition) END || " +
                        "           CASE WHEN COALESCE(TRIM(r.remarks), '') = '' THEN '' ELSE ' | Remarks: ' || TRIM(r.remarks) END || " +
                        "           CASE WHEN COALESCE(TRIM(r.phone), '') = '' THEN '' ELSE ' | Phone: ' || TRIM(r.phone) END || " +
                        "           CASE WHEN COALESCE(TRIM(r.nid), '') = '' THEN '' ELSE ' | NID: ' || TRIM(r.nid) END AS details, " +
                        "           'AVAILABLE' AS status, " +
                        "           2 AS event_order, r.id AS record_id " +
                        "    FROM returns r WHERE LOWER(TRIM(r.asset_code)) = LOWER(TRIM(?)) " +
                        ") history ORDER BY COALESCE(activity_date, CURRENT_DATE) DESC, event_order DESC, record_id DESC",
                (rs, rowNum) -> new AssetHistoryRecordResponse(
                        rs.getString("activity_date"),
                        rs.getString("event_type"),
                        rs.getString("actor"),
                        rs.getString("affected_person"),
                        rs.getString("details"),
                        rs.getString("status")
                ),
                assetCode, assetCode, assetCode, assetCode
        );

        return new AssetHistoryResponse(summaries.get(0), records);
    }

    public CommonMessageResponse logAuditEntry(String requesterIdentifier, AuditLogRequest request) {
        String actor = normalize(request.username());
        if (actor.isBlank()) {
            actor = normalize(requesterIdentifier);
        }
        jdbcTemplate.update(
                "INSERT INTO audit_log(action, entity, performed_by, username, module_name, details, action_time) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                normalize(request.action()),
                normalize(request.moduleName()),
                actor,
                actor,
                normalize(request.moduleName()),
                normalize(request.details())
        );
        return new CommonMessageResponse(true, "Audit log saved.");
    }

    public List<AuditLogResponse> getAuditLogs(String requesterIdentifier, String username) {
        UserAccount requester = userManagementService.getUser(requesterIdentifier);
        String requesterRole = requester.getRole().name();
        if (!"SUPER_ADMIN".equalsIgnoreCase(requesterRole) && !"ADMIN".equalsIgnoreCase(requesterRole)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Access denied.");
        }
        recordAuditLogView(requester);
        String effectiveUsername = normalize(username);
        boolean includeSuperAdminLogs = "SUPER_ADMIN".equalsIgnoreCase(requesterRole);

        String sql =
                "SELECT log.id, " +
                        "COALESCE(NULLIF(TRIM(log.username), ''), NULLIF(TRIM(log.performed_by), ''), 'unknown_user') AS log_username, " +
                        "log.action, " +
                        "COALESCE(NULLIF(TRIM(log.module_name), ''), NULLIF(TRIM(log.entity), ''), '') AS log_module_name, " +
                        "log.details, log.action_time " +
                        "FROM audit_log log " +
                        "LEFT JOIN users actor_user ON " +
                        "LOWER(actor_user.email) = LOWER(COALESCE(NULLIF(TRIM(log.username), ''), NULLIF(TRIM(log.performed_by), ''), '')) " +
                        "OR LOWER(actor_user.username) = LOWER(COALESCE(NULLIF(TRIM(log.username), ''), NULLIF(TRIM(log.performed_by), ''), '')) ";
        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        if (!effectiveUsername.isBlank()) {
            conditions.add("LOWER(COALESCE(NULLIF(TRIM(log.username), ''), NULLIF(TRIM(log.performed_by), ''), '')) = LOWER(?)");
            params.add(effectiveUsername);
        }
        if (!includeSuperAdminLogs) {
            conditions.add("UPPER(actor_user.role) IN ('ADMIN', 'USER')");
        }
        if (!conditions.isEmpty()) {
            sql += "WHERE " + String.join(" AND ", conditions) + " ";
        }

        return jdbcTemplate.query(sql + "ORDER BY log.action_time DESC, log.id DESC",
                (rs, rowNum) -> mapAuditLog(rs.getInt("id"), rs.getString("log_username"), rs.getString("action"),
                        rs.getString("log_module_name"), rs.getString("details"), rs.getTimestamp("action_time")),
                params.toArray());
    }

    private AuditLogResponse mapAuditLog(int id, String username, String action, String moduleName, String details, Timestamp actionTime) {
        return new AuditLogResponse(
                id,
                username,
                action,
                moduleName,
                details,
                actionTime == null ? "" : actionTime.toLocalDateTime().format(DATE_TIME_FORMATTER)
        );
    }

    private void recordAuditLogView(UserAccount requester) {
        String actor = normalize(requester == null ? "" : requester.getEmail());
        if (actor.isBlank() && requester != null) {
            actor = normalize(requester.getUsername());
        }
        if (actor.isBlank()) {
            actor = "unknown_user";
        }
        jdbcTemplate.update(
                "INSERT INTO audit_log(action, entity, performed_by, username, module_name, details, action_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                "VIEW_AUDIT_LOGS",
                "AUDIT",
                actor,
                actor,
                "AUDIT",
                "Audit logs viewed."
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
