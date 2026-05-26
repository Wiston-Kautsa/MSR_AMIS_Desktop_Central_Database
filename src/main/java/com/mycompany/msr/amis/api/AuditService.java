package com.mycompany.msr.amis;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class AuditService {

    private static final ApiClient API_CLIENT = new ApiClient(AppConfiguration.load().getApiBaseUrl());

    private AuditService() {
    }

    public static void log(String username, String action, String moduleName, String details) {
        if (ServiceRegistry.getConfiguration().usesLocalDatabase()) {
            logLocal(username, action, moduleName, details);
            return;
        }

        try {
            API_CLIENT.post("/api/audit-logs",
                    new AuditLogRequest(normalizeUsername(username), normalize(action), normalize(moduleName), normalize(details)),
                    AuditMessageResponse.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ObservableList<AuditLog> getAllLogs() {
        return getLogs(null);
    }

    public static ObservableList<AuditLog> getLogsByUsername(String username) {
        return getLogs(normalizeUsername(username));
    }

    private static ObservableList<AuditLog> getLogs(String username) {
        if (ServiceRegistry.getConfiguration().usesLocalDatabase()) {
            return getLogsLocal(username);
        }

        try {
            String path = username == null || username.isBlank()
                    ? "/api/audit-logs"
                    : "/api/audit-logs?username=" + java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8);
            AuditLogPayload[] payloads = API_CLIENT.get(path, AuditLogPayload[].class);
            ObservableList<AuditLog> logs = FXCollections.observableArrayList();
            if (payloads != null) {
                for (AuditLogPayload payload : payloads) {
                    logs.add(new AuditLog(
                            payload.id,
                            payload.username,
                            payload.action,
                            payload.moduleName,
                            payload.details,
                            payload.actionTime
                    ));
                }
            }
            return logs.isEmpty() ? getLogsLocal(username) : logs;
        } catch (Exception e) {
            e.printStackTrace();
            return getLogsLocal(username);
        }
    }

    private static void logLocal(String username, String action, String moduleName, String details) {
        String sql =
                "INSERT INTO audit_log (username, action, module_name, details, performed_by) " +
                "VALUES (?, ?, ?, ?, ?)";

        String normalizedUsername = normalizeUsername(username);

        try (Connection conn = DatabaseHandler.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, normalizedUsername);
            ps.setString(2, normalize(action));
            ps.setString(3, normalize(moduleName));
            ps.setString(4, normalize(details));
            ps.setString(5, normalizedUsername);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ObservableList<AuditLog> getLogsLocal(String username) {
        ObservableList<AuditLog> logs = FXCollections.observableArrayList();
        String sql =
                "SELECT id, " +
                "COALESCE(NULLIF(TRIM(username), ''), NULLIF(TRIM(performed_by), ''), 'unknown_user') AS log_username, " +
                "action, " +
                "COALESCE(NULLIF(TRIM(module_name), ''), NULLIF(TRIM(entity), ''), '') AS log_module_name, " +
                "details, action_time " +
                "FROM audit_log ";

        if (username != null && !username.isBlank()) {
            sql += "WHERE LOWER(COALESCE(NULLIF(TRIM(username), ''), NULLIF(TRIM(performed_by), ''), '')) = LOWER(?) ";
        }

        sql += "ORDER BY action_time DESC, id DESC";

        try (Connection conn = DatabaseHandler.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (username != null && !username.isBlank()) {
                ps.setString(1, username);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(new AuditLog(
                            rs.getInt("id"),
                            rs.getString("log_username"),
                            rs.getString("action"),
                            rs.getString("log_module_name"),
                            rs.getString("details"),
                            rs.getString("action_time")
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return logs;
    }

    private static String normalizeUsername(String username) {
        String normalized = normalize(username);
        return normalized.isBlank() ? "unknown_user" : normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class AuditLogRequest {
        public String username;
        public String action;
        public String moduleName;
        public String details;

        private AuditLogRequest(String username, String action, String moduleName, String details) {
            this.username = username;
            this.action = action;
            this.moduleName = moduleName;
            this.details = details;
        }
    }

    private static final class AuditLogPayload {
        public int id;
        public String username;
        public String action;
        public String moduleName;
        public String details;
        public String actionTime;
    }

    private static final class AuditMessageResponse {
        public boolean success;
        public String message;
    }
}
