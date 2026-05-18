package com.mycompany.msr.amis.api.service;

import com.mycompany.msr.amis.api.dto.maintenance.MaintenanceRequest;
import com.mycompany.msr.amis.api.dto.maintenance.MaintenanceResponse;
import com.mycompany.msr.amis.api.exception.ApiException;
import java.sql.Date;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceService {

    private final JdbcTemplate jdbcTemplate;
    private final ActionAuditService actionAuditService;

    public MaintenanceService(JdbcTemplate jdbcTemplate, ActionAuditService actionAuditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.actionAuditService = actionAuditService;
    }

    public List<MaintenanceResponse> getMaintenanceRecords() {
        return jdbcTemplate.query(
                "SELECT id, asset_code, issue, action_taken, performed_by, maintenance_date, cost, status " +
                        "FROM maintenance_log ORDER BY maintenance_date DESC, id DESC",
                (rs, rowNum) -> new MaintenanceResponse(
                        rs.getLong("id"),
                        rs.getString("asset_code"),
                        rs.getString("issue"),
                        rs.getString("action_taken"),
                        rs.getString("performed_by"),
                        rs.getDate("maintenance_date") == null ? "" : rs.getDate("maintenance_date").toLocalDate().toString(),
                        rs.getString("cost"),
                        rs.getString("status")
                )
        );
    }

    @Transactional
    public MaintenanceResponse createMaintenanceRecord(String actor, MaintenanceRequest request) {
        String assetCode = normalizeRequired(request.assetCode(), "Asset code is required.");
        String issue = normalizeRequired(request.issue(), "Maintenance issue is required.");
        assertEquipmentExists(assetCode);

        String status = request.completed() ? "COMPLETED" : "OPEN";
        LocalDate maintenanceDate = parseDate(request.maintenanceDate());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO maintenance_log(asset_code, issue, action_taken, performed_by, maintenance_date, cost, status, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, assetCode);
            ps.setString(2, issue);
            ps.setString(3, normalize(request.actionTaken()));
            ps.setString(4, normalize(request.performedBy()));
            ps.setDate(5, Date.valueOf(maintenanceDate));
            ps.setString(6, normalize(request.cost()));
            ps.setString(7, status);
            return ps;
        }, keyHolder);

        Number id = keyHolder.getKey();
        if (id == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Maintenance record was saved but its id could not be resolved.");
        }

        jdbcTemplate.update(
                "UPDATE equipment SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE LOWER(TRIM(asset_code)) = LOWER(TRIM(?))",
                request.completed() ? "AVAILABLE" : "MAINTENANCE",
                assetCode
        );
        actionAuditService.log(
                actor,
                "LOG_MAINTENANCE",
                "MAINTENANCE",
                assetCode,
                "Maintenance logged for " + assetCode + ". Status: " + status + ". Issue: " + issue
        );
        return getMaintenanceRecord(id.longValue());
    }

    private MaintenanceResponse getMaintenanceRecord(long id) {
        List<MaintenanceResponse> rows = jdbcTemplate.query(
                "SELECT id, asset_code, issue, action_taken, performed_by, maintenance_date, cost, status " +
                        "FROM maintenance_log WHERE id = ?",
                (rs, rowNum) -> new MaintenanceResponse(
                        rs.getLong("id"),
                        rs.getString("asset_code"),
                        rs.getString("issue"),
                        rs.getString("action_taken"),
                        rs.getString("performed_by"),
                        rs.getDate("maintenance_date") == null ? "" : rs.getDate("maintenance_date").toLocalDate().toString(),
                        rs.getString("cost"),
                        rs.getString("status")
                ),
                id
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Maintenance record not found.");
        }
        return rows.get(0);
    }

    private void assertEquipmentExists(String assetCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM equipment WHERE LOWER(TRIM(asset_code)) = LOWER(TRIM(?))",
                Integer.class,
                assetCode
        );
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Equipment record was not found.");
        }
    }

    private LocalDate parseDate(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(normalized);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid maintenance date.");
        }
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
}
