package com.mycompany.msr.amis;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LocalDataMaintenanceService implements DataMaintenanceService {

    private final RemoteMirrorCoordinator remoteMirrorCoordinator = ServiceRegistry.getRemoteMirrorCoordinator();

    @Override
    public DataMaintenanceSummary getSummary() throws Exception {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        try (Connection connection = DatabaseHandler.getConnection()) {
            return new DataMaintenanceSummary(
                    count(connection, "equipment"),
                    count(connection, "assignments"),
                    count(connection, "distribution"),
                    count(connection, "returns"),
                    count(connection, "audit_log")
            );
        }
    }

    @Override
    public String resetComponent(String component) throws Exception {
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            String message = remoteMirrorCoordinator.getRemoteDataMaintenanceService().resetComponent(component);
            remoteMirrorCoordinator.synchronizeFromRemote(Map.of());
            return message;
        }
        String normalized = normalize(component);
        switch (normalized) {
            case "RETURNS":
                return resetReturns();
            case "DISTRIBUTION":
                return resetDistribution();
            case "ASSIGNMENTS":
                return resetAssignments();
            case "EQUIPMENT":
                return resetEquipment();
            case "AUDIT_LOG":
                return resetAuditLog();
            case "FULL_OPERATIONAL_DATA":
                return resetFullOperationalData();
            default:
                throw new IllegalArgumentException("Unsupported maintenance component: " + component);
        }
    }

    private String resetReturns() throws Exception {
        try (Connection connection = DatabaseHandler.getConnection()) {
            int deleted = deleteTable(connection, "returns");
            resetSequence(connection, "returns");
            DatabaseHandler.logAudit("RESET_RETURNS_DATA", "SYSTEM", "returns", "Local returns data cleared. Deleted rows: " + deleted + ".");
            return "Local returns data cleared. Deleted rows: " + deleted + ".";
        }
    }

    private String resetDistribution() throws Exception {
        try (Connection connection = DatabaseHandler.getConnection()) {
            ensureEmpty(connection, "returns", "Clear returns before distribution so historical return records are not orphaned.");
            int deleted = deleteTable(connection, "distribution");
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE equipment SET status = 'AVAILABLE' WHERE UPPER(COALESCE(status, 'AVAILABLE')) = 'ASSIGNED'"
            )) {
                ps.executeUpdate();
            }
            resetSequence(connection, "distribution");
            DatabaseHandler.logAudit("RESET_DISTRIBUTION_DATA", "SYSTEM", "distribution",
                    "Local distribution data cleared. Deleted rows: " + deleted + ". Assigned equipment statuses were returned to AVAILABLE.");
            return "Local distribution data cleared. Deleted rows: " + deleted + ". Assigned equipment statuses were returned to AVAILABLE.";
        }
    }

    private String resetAssignments() throws Exception {
        try (Connection connection = DatabaseHandler.getConnection()) {
            ensureEmpty(connection, "returns", "Clear returns before assignments.");
            ensureEmpty(connection, "distribution", "Clear distribution before assignments.");
            int deleted = deleteTable(connection, "assignments");
            resetSequence(connection, "assignments");
            DatabaseHandler.logAudit("RESET_ASSIGNMENTS_DATA", "SYSTEM", "assignments",
                    "Local assignment data cleared. Deleted rows: " + deleted + ".");
            return "Local assignment data cleared. Deleted rows: " + deleted + ".";
        }
    }

    private String resetEquipment() throws Exception {
        try (Connection connection = DatabaseHandler.getConnection()) {
            ensureEmpty(connection, "returns", "Clear returns before equipment.");
            ensureEmpty(connection, "distribution", "Clear distribution before equipment.");
            ensureEmpty(connection, "assignments", "Clear assignments before equipment.");
            int deleted = deleteTable(connection, "equipment");
            resetSequence(connection, "equipment");
            DatabaseHandler.logAudit("RESET_EQUIPMENT_DATA", "SYSTEM", "equipment",
                    "Local equipment data cleared. Deleted rows: " + deleted + ".");
            return "Local equipment data cleared. Deleted rows: " + deleted + ".";
        }
    }

    private String resetAuditLog() throws Exception {
        try (Connection connection = DatabaseHandler.getConnection()) {
            int deleted = deleteTable(connection, "audit_log");
            resetSequence(connection, "audit_log");
            DatabaseHandler.logAudit("RESET_AUDIT_LOG_DATA", "SYSTEM", "audit_log",
                    "Local audit log cleared. Deleted rows: " + deleted + ".");
            return "Local audit log cleared. Deleted rows: " + deleted + ".";
        }
    }

    private String resetFullOperationalData() throws Exception {
        Map<String, Integer> deletedRows = new LinkedHashMap<>();
        try (Connection connection = DatabaseHandler.getConnection()) {
            deletedRows.put("returns", deleteTable(connection, "returns"));
            deletedRows.put("distribution", deleteTable(connection, "distribution"));
            deletedRows.put("assignments", deleteTable(connection, "assignments"));
            deletedRows.put("equipment", deleteTable(connection, "equipment"));
            deletedRows.put("password_reset_audit", deleteTable(connection, "password_reset_audit"));
            deletedRows.put("audit_log", deleteTable(connection, "audit_log"));
            resetSequence(connection, "returns");
            resetSequence(connection, "distribution");
            resetSequence(connection, "assignments");
            resetSequence(connection, "equipment");
            resetSequence(connection, "password_reset_audit");
            resetSequence(connection, "audit_log");
        }

        StringBuilder message = new StringBuilder("Local operational data reset completed. Preserved users and departments.");
        for (Map.Entry<String, Integer> entry : deletedRows.entrySet()) {
            message.append(" ").append(entry.getKey()).append("=").append(entry.getValue()).append(";");
        }
        DatabaseHandler.logAudit("RESET_FULL_OPERATIONAL_DATA", "SYSTEM", "operational_data", message.toString());
        return message.toString();
    }

    private int count(Connection connection, String tableName) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void ensureEmpty(Connection connection, String tableName, String message) throws Exception {
        if (count(connection, tableName) > 0) {
            throw new IllegalStateException(message);
        }
    }

    private int deleteTable(Connection connection, String tableName) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + tableName)) {
            return ps.executeUpdate();
        }
    }

    private void resetSequence(Connection connection, String tableName) throws Exception {
        if (!sqliteSequenceExists(connection)) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM sqlite_sequence WHERE name = ?")) {
            ps.setString(1, tableName);
            ps.executeUpdate();
        }
    }

    private boolean sqliteSequenceExists(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'sqlite_sequence' LIMIT 1"
             )) {
            return rs.next();
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
