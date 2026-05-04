package com.mycompany.msr.amis.api.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mycompany.msr.amis.api.exception.ApiException;

@Service
public class SystemAdminService {

    private final JdbcTemplate jdbcTemplate;
    private final ActionAuditService actionAuditService;

    public SystemAdminService(JdbcTemplate jdbcTemplate, ActionAuditService actionAuditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.actionAuditService = actionAuditService;
    }

    @Transactional
    public String resetOperationalData(String actor) {
        Map<String, Integer> deletedRows = new LinkedHashMap<>();
        deletedRows.put("returns", jdbcTemplate.update("DELETE FROM returns"));
        deletedRows.put("distribution", jdbcTemplate.update("DELETE FROM distribution"));
        deletedRows.put("assignments", jdbcTemplate.update("DELETE FROM assignments"));
        deletedRows.put("equipment", jdbcTemplate.update("DELETE FROM equipment"));
        deletedRows.put("password_reset_audit", jdbcTemplate.update("DELETE FROM password_reset_audit"));
        deletedRows.put("audit_log", jdbcTemplate.update("DELETE FROM audit_log"));

        resetSequence("returns_id_seq");
        resetSequence("distribution_id_seq");
        resetSequence("assignments_id_seq");
        resetSequence("equipment_id_seq");
        resetSequence("password_reset_audit_id_seq");
        resetSequence("audit_log_id_seq");

        StringJoiner joiner = new StringJoiner(", ");
        deletedRows.forEach((table, count) -> joiner.add(table + "=" + count));
        String summary = "Remote operational data reset completed. Preserved users and departments. Cleared: " + joiner + ".";

        actionAuditService.log(
                actor,
                "RESET_FULL_OPERATIONAL_DATA",
                "SYSTEM",
                "operational_data",
                summary
        );
        return summary;
    }

    public DataMaintenanceSummary getSummary() {
        return new DataMaintenanceSummary(
                count("equipment"),
                count("assignments"),
                count("distribution"),
                count("returns"),
                count("audit_log")
        );
    }

    @Transactional
    public String resetComponent(String actor, String rawComponent) {
        ResetComponent component = ResetComponent.from(rawComponent);
        return switch (component) {
            case RETURNS -> resetReturns(actor);
            case DISTRIBUTION -> resetDistribution(actor);
            case ASSIGNMENTS -> resetAssignments(actor);
            case EQUIPMENT -> resetEquipment(actor);
            case AUDIT_LOG -> resetAuditLog(actor);
            case FULL_OPERATIONAL_DATA -> resetOperationalData(actor);
        };
    }

    private String resetReturns(String actor) {
        int deleted = jdbcTemplate.update("DELETE FROM returns");
        resetSequence("returns_id_seq");
        return logReset(actor, "RESET_RETURNS_DATA", "returns", deleted, "Remote returns data cleared.");
    }

    private String resetDistribution(String actor) {
        ensureEmpty("returns", "Clear returns before distribution so historical return records are not orphaned.");
        int deleted = jdbcTemplate.update("DELETE FROM distribution");
        jdbcTemplate.update("UPDATE equipment SET status = 'AVAILABLE' WHERE status = 'ASSIGNED'");
        resetSequence("distribution_id_seq");
        return logReset(actor, "RESET_DISTRIBUTION_DATA", "distribution", deleted, "Remote distribution data cleared. Assigned equipment statuses were returned to AVAILABLE.");
    }

    private String resetAssignments(String actor) {
        ensureEmpty("returns", "Clear returns before assignments.");
        ensureEmpty("distribution", "Clear distribution before assignments.");
        int deleted = jdbcTemplate.update("DELETE FROM assignments");
        resetSequence("assignments_id_seq");
        return logReset(actor, "RESET_ASSIGNMENTS_DATA", "assignments", deleted, "Remote assignment data cleared.");
    }

    private String resetEquipment(String actor) {
        ensureEmpty("returns", "Clear returns before equipment.");
        ensureEmpty("distribution", "Clear distribution before equipment.");
        ensureEmpty("assignments", "Clear assignments before equipment.");
        int deleted = jdbcTemplate.update("DELETE FROM equipment");
        resetSequence("equipment_id_seq");
        return logReset(actor, "RESET_EQUIPMENT_DATA", "equipment", deleted, "Remote equipment data cleared.");
    }

    private String resetAuditLog(String actor) {
        int deleted = jdbcTemplate.update("DELETE FROM audit_log");
        resetSequence("audit_log_id_seq");
        String message = "Remote audit log cleared. Deleted rows: " + deleted + ".";
        actionAuditService.log(
                actor,
                "RESET_AUDIT_LOG_DATA",
                "SYSTEM",
                "",
                message
        );
        return message;
    }

    private String logReset(String actor, String action, String component, int deleted, String prefix) {
        String message = prefix + " Deleted rows: " + deleted + ".";
        actionAuditService.log(actor, action, "SYSTEM", component, message);
        return message;
    }

    private void ensureEmpty(String tableName, String message) {
        if (count(tableName) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private int count(String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private void resetSequence(String sequenceName) {
        jdbcTemplate.execute("ALTER SEQUENCE " + sequenceName + " RESTART WITH 1");
    }

    public record DataMaintenanceSummary(
            int equipmentCount,
            int assignmentCount,
            int distributionCount,
            int returnCount,
            int auditLogCount
    ) {
    }

    private enum ResetComponent {
        RETURNS,
        DISTRIBUTION,
        ASSIGNMENTS,
        EQUIPMENT,
        AUDIT_LOG,
        FULL_OPERATIONAL_DATA;

        private static ResetComponent from(String rawValue) {
            if (rawValue == null || rawValue.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Maintenance component is required.");
            }
            try {
                return ResetComponent.valueOf(rawValue.trim().toUpperCase());
            } catch (IllegalArgumentException exception) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported maintenance component: " + rawValue);
            }
        }
    }
}
