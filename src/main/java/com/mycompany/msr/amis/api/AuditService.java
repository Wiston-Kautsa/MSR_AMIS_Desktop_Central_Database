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

    public static ObservableList<AuditLog> getRecentLogs(int limit) {
        return getLogs(null, limit);
    }

    public static ObservableList<AuditLog> getLogsByUsername(String username) {
        return getLogs(normalizeUsername(username));
    }

    private static ObservableList<AuditLog> getLogs(String username) {
        return getLogs(username, 0);
    }

    private static ObservableList<AuditLog> getLogs(String username, int limit) {
        if (ServiceRegistry.getConfiguration().usesLocalDatabase()) {
            return getLogsLocal(username, limit);
        }

        try {
            String path = buildAuditLogsPath(username, limit);
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
            return logs;
        } catch (Exception e) {
            e.printStackTrace();
            return getLogsLocal(username, limit);
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
        return getLogsLocal(username, 0);
    }

    private static ObservableList<AuditLog> getLogsLocal(String username, int limit) {
        ObservableList<AuditLog> logs = FXCollections.observableArrayList();
        String sql =
                "SELECT log.id, " +
                "COALESCE(NULLIF(TRIM(log.username), ''), NULLIF(TRIM(log.performed_by), ''), 'unknown_user') AS log_username, " +
                "log.action, " +
                "COALESCE(NULLIF(TRIM(log.module_name), ''), NULLIF(TRIM(log.entity), ''), '') AS log_module_name, " +
                "log.details, log.action_time " +
                "FROM audit_log log ";

        boolean hasFilter = false;
        if (username != null && !username.isBlank()) {
            sql += "WHERE LOWER(COALESCE(NULLIF(TRIM(log.username), ''), NULLIF(TRIM(log.performed_by), ''), '')) = LOWER(?) ";
            hasFilter = true;
        }
        if (!Session.hasRole(AccessControl.ROLE_SUPER_ADMIN)) {
            sql += hasFilter ? "AND " : "WHERE ";
            sql += "EXISTS (" +
                    "SELECT 1 FROM users actor_user " +
                    "WHERE UPPER(actor_user.role) IN ('ADMIN', 'USER') " +
                    "AND (" +
                    "LOWER(actor_user.email) = LOWER(COALESCE(NULLIF(TRIM(log.username), ''), NULLIF(TRIM(log.performed_by), ''), '')) " +
                    "OR LOWER(actor_user.username) = LOWER(COALESCE(NULLIF(TRIM(log.username), ''), NULLIF(TRIM(log.performed_by), ''), ''))" +
                    ")) ";
        }

        sql += "ORDER BY log.action_time DESC, log.id DESC";
        int normalizedLimit = normalizeLimit(limit);
        if (normalizedLimit > 0) {
            sql += " LIMIT ?";
        }

        try (Connection conn = DatabaseHandler.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int parameterIndex = 1;
            if (username != null && !username.isBlank()) {
                ps.setString(parameterIndex++, username);
            }
            if (normalizedLimit > 0) {
                ps.setInt(parameterIndex, normalizedLimit);
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

    private static String buildAuditLogsPath(String username, int limit) {
        StringBuilder path = new StringBuilder("/api/audit-logs");
        String separator = "?";
        if (username != null && !username.isBlank()) {
            path.append(separator)
                    .append("username=")
                    .append(java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8));
            separator = "&";
        }
        int normalizedLimit = normalizeLimit(limit);
        if (normalizedLimit > 0) {
            path.append(separator).append("limit=").append(normalizedLimit);
        }
        return path.toString();
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 0;
        }
        return Math.min(limit, 5000);
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
