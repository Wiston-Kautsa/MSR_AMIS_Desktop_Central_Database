package com.mycompany.msr.amis;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LocalMirrorRepository {

    public void synchronizeFromRemote(List<String> departments,
                                      List<User> users,
                                      Map<String, String> passwordOverridesByEmail,
                                      List<Equipment> equipment,
                                      List<Assignment> assignments,
                                      List<Distribution> distributions,
                                      List<ReturnRecord> returns,
                                      List<AuditLog> auditLogs) throws Exception {
        if (users == null || users.isEmpty()) {
            throw new IllegalStateException("Central synchronization failed because no users were returned.");
        }

        try (Connection connection = DatabaseHandler.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, CredentialSnapshot> existingCredentials = loadCredentialSnapshots(connection);

                deleteAll(connection, "returns");
                deleteAll(connection, "distribution");
                deleteAll(connection, "assignments");
                deleteAll(connection, "equipment");
                deleteAll(connection, "audit_log");
                deleteAll(connection, "users");
                deleteAll(connection, "departments");

                insertDepartments(connection, departments, users);
                insertUsers(connection, users, existingCredentials, passwordOverridesByEmail);
                insertEquipment(connection, equipment);
                insertAssignments(connection, assignments);
                insertDistributions(connection, distributions);
                insertReturns(connection, returns);
                insertAuditLogs(connection, auditLogs);

                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void upsertAuthenticatedUser(User user, String hashedPassword) throws Exception {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        try (Connection connection = DatabaseHandler.getConnection()) {
            connection.setAutoCommit(false);
            try {
                insertDepartmentIfMissing(connection, user.getDepartment());

                Integer existingId = findExistingUserId(connection, user.getEmail(), user.getUsername());
                if (existingId == null) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO users (full_name, username, password, role, status, temporary, department, phone, email) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    )) {
                        ps.setString(1, normalize(user.getFullName()));
                        ps.setString(2, normalize(user.getUsername()));
                        ps.setString(3, fallbackPassword(user.getEmail(), hashedPassword));
                        ps.setString(4, normalize(user.getRole()));
                        ps.setString(5, normalizeStatus(user.getStatus()));
                        ps.setInt(6, AccessControl.isTemporarySetupAccountEmail(user.getEmail()) ? 1 : 0);
                        ps.setString(7, normalize(user.getDepartment()));
                        ps.setString(8, normalize(user.getPhone()));
                        ps.setString(9, normalize(user.getEmail()));
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE users SET full_name=?, username=?, password=?, role=?, status=?, temporary=?, department=?, phone=?, email=? WHERE id=?"
                    )) {
                        ps.setString(1, normalize(user.getFullName()));
                        ps.setString(2, normalize(user.getUsername()));
                        ps.setString(3, fallbackPassword(user.getEmail(), hashedPassword));
                        ps.setString(4, normalize(user.getRole()));
                        ps.setString(5, normalizeStatus(user.getStatus()));
                        ps.setInt(6, AccessControl.isTemporarySetupAccountEmail(user.getEmail()) ? 1 : 0);
                        ps.setString(7, normalize(user.getDepartment()));
                        ps.setString(8, normalize(user.getPhone()));
                        ps.setString(9, normalize(user.getEmail()));
                        ps.setInt(10, existingId);
                        ps.executeUpdate();
                    }
                }

                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void updateMirroredPassword(String identifier, String hashedPassword) throws Exception {
        if (identifier == null || identifier.isBlank() || hashedPassword == null || hashedPassword.isBlank()) {
            return;
        }
        try (Connection connection = DatabaseHandler.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE users SET password=?, last_password_reset=CURRENT_TIMESTAMP " +
                             "WHERE LOWER(email)=LOWER(?) OR LOWER(username)=LOWER(?)"
             )) {
            ps.setString(1, hashedPassword);
            ps.setString(2, identifier.trim());
            ps.setString(3, identifier.trim());
            ps.executeUpdate();
        }
    }

    private Map<String, CredentialSnapshot> loadCredentialSnapshots(Connection connection) throws Exception {
        Map<String, CredentialSnapshot> snapshots = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT username, email, password FROM users"
        ); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CredentialSnapshot snapshot = new CredentialSnapshot(
                        normalize(rs.getString("username")),
                        normalize(rs.getString("email")),
                        normalize(rs.getString("password"))
                );
                if (!snapshot.email.isBlank()) {
                    snapshots.put("email:" + snapshot.email.toLowerCase(), snapshot);
                }
                if (!snapshot.username.isBlank()) {
                    snapshots.put("username:" + snapshot.username.toLowerCase(), snapshot);
                }
            }
        }
        return snapshots;
    }

    private void insertDepartments(Connection connection, List<String> departments, List<User> users) throws Exception {
        insertDepartmentIfMissing(connection, "MSR");
        if (departments != null) {
            for (String department : departments) {
                insertDepartmentIfMissing(connection, department);
            }
        }
        for (User user : users) {
            if (user != null) {
                insertDepartmentIfMissing(connection, user.getDepartment());
            }
        }
    }

    private void insertDepartmentIfMissing(Connection connection, String department) throws Exception {
        String normalizedDepartment = normalize(department);
        if (normalizedDepartment.isBlank()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO departments(name) VALUES (?)"
        )) {
            ps.setString(1, normalizedDepartment);
            ps.executeUpdate();
        }
    }

    private void insertUsers(Connection connection,
                             List<User> users,
                             Map<String, CredentialSnapshot> existingCredentials,
                             Map<String, String> passwordOverridesByEmail) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO users (id, full_name, username, password, role, status, temporary, department, phone, email) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            for (User user : users) {
                if (user == null) {
                    continue;
                }
                String email = normalize(user.getEmail());
                String username = normalize(user.getUsername());
                String password = resolvePassword(email, username, existingCredentials, passwordOverridesByEmail);

                ps.setInt(1, user.getId());
                ps.setString(2, normalize(user.getFullName()));
                ps.setString(3, username);
                ps.setString(4, password);
                ps.setString(5, normalize(user.getRole()));
                ps.setString(6, normalizeStatus(user.getStatus()));
                ps.setInt(7, AccessControl.isTemporarySetupAccountEmail(email) ? 1 : 0);
                ps.setString(8, normalize(user.getDepartment()));
                ps.setString(9, normalize(user.getPhone()));
                ps.setString(10, email);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertEquipment(Connection connection, List<Equipment> equipment) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO equipment (id, asset_code, name, category, serial_number, condition, source, entry_date, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            for (Equipment item : equipment) {
                if (item == null) {
                    continue;
                }
                ps.setInt(1, item.getId());
                ps.setString(2, normalize(item.getAssetCode()));
                ps.setString(3, normalize(item.getName()));
                ps.setString(4, normalize(item.getCategory()));
                ps.setString(5, normalize(item.getSerialNumber()));
                ps.setString(6, normalize(item.getCondition()));
                ps.setString(7, normalize(item.getSource()));
                ps.setString(8, normalize(item.getEntryDate()));
                ps.setString(9, normalize(item.getStatus()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertAssignments(Connection connection, List<Assignment> assignments) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO assignments (id, person, department, equipment_type, reason, quantity, status, date) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            for (Assignment assignment : assignments) {
                if (assignment == null) {
                    continue;
                }
                ps.setInt(1, assignment.getId());
                ps.setString(2, normalize(assignment.getPerson()));
                ps.setString(3, normalize(assignment.getDepartment()));
                ps.setString(4, normalize(assignment.getEquipmentType()));
                ps.setString(5, normalize(assignment.getReason()));
                ps.setInt(6, assignment.getQuantity());
                ps.setString(7, normalizeStatus(assignment.getStatus()));
                ps.setString(8, normalize(assignment.getDate()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertDistributions(Connection connection, List<Distribution> distributions) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO distribution (id, asset_code, assignment_id, assigned_to, phone, nid, outstanding_remarks, date, returned) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            for (Distribution distribution : distributions) {
                if (distribution == null) {
                    continue;
                }
                ps.setInt(1, distribution.getId());
                ps.setString(2, normalize(distribution.getAssetCode()));
                ps.setInt(3, distribution.getAssignmentId());
                ps.setString(4, normalize(distribution.getAssignedTo()));
                ps.setString(5, normalize(distribution.getPhone()));
                ps.setString(6, normalize(distribution.getNid()));
                ps.setString(7, normalize(distribution.getOutstandingRemarks()));
                ps.setString(8, normalize(distribution.getDate()));
                ps.setInt(9, isReturnedStatus(distribution.getStatus()) ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertReturns(Connection connection, List<ReturnRecord> returns) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO returns (asset_code, returned_by, phone, nid, condition, remarks, return_date) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)"
        )) {
            for (ReturnRecord record : returns) {
                if (record == null) {
                    continue;
                }
                ps.setString(1, normalize(record.getAssetCode()));
                ps.setString(2, normalize(record.getReturnedBy()));
                ps.setString(3, normalize(record.getPhone()));
                ps.setString(4, normalize(record.getNid()));
                ps.setString(5, normalize(record.getReturnCondition()));
                ps.setString(6, normalize(record.getRemarks()));
                ps.setString(7, normalize(record.getReturnDate()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertAuditLogs(Connection connection, List<AuditLog> auditLogs) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO audit_log (id, username, action, module_name, details, action_time, performed_by, entity) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            for (AuditLog auditLog : auditLogs) {
                if (auditLog == null) {
                    continue;
                }
                ps.setInt(1, auditLog.getId());
                ps.setString(2, normalize(auditLog.getUsername()));
                ps.setString(3, normalize(auditLog.getAction()));
                ps.setString(4, normalize(auditLog.getModuleName()));
                ps.setString(5, normalize(auditLog.getDetails()));
                ps.setString(6, normalize(auditLog.getActionTime()));
                ps.setString(7, normalize(auditLog.getUsername()));
                ps.setString(8, normalize(auditLog.getModuleName()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private Integer findExistingUserId(Connection connection, String email, String username) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM users WHERE LOWER(email)=LOWER(?) OR LOWER(username)=LOWER(?) LIMIT 1"
        )) {
            ps.setString(1, normalize(email));
            ps.setString(2, normalize(username));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private void deleteAll(Connection connection, String tableName) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM " + tableName);
        }
    }

    private String resolvePassword(String email,
                                   String username,
                                   Map<String, CredentialSnapshot> existingCredentials,
                                   Map<String, String> passwordOverridesByEmail) {
        if (email != null && passwordOverridesByEmail != null) {
            String override = passwordOverridesByEmail.get(email.toLowerCase());
            if (override != null && !override.isBlank()) {
                return override;
            }
        }

        CredentialSnapshot byEmail = email == null ? null : existingCredentials.get("email:" + email.toLowerCase());
        if (byEmail != null && !byEmail.password.isBlank()) {
            return byEmail.password;
        }

        CredentialSnapshot byUsername = username == null ? null : existingCredentials.get("username:" + username.toLowerCase());
        if (byUsername != null && !byUsername.password.isBlank()) {
            return byUsername.password;
        }

        return fallbackPassword(email, "");
    }

    private String fallbackPassword(String email, String hashedPassword) {
        if (hashedPassword != null && !hashedPassword.isBlank()) {
            return hashedPassword;
        }
        if (AccessControl.isTemporarySetupAccountEmail(email) || AccessControl.isPrimarySuperAdminEmail(email)) {
            return PasswordUtils.hash("admin123");
        }
        return "";
    }

    private boolean isReturnedStatus(String status) {
        String normalized = normalize(status).toUpperCase();
        return "RETURNED".equals(normalized);
    }

    private String normalizeStatus(String status) {
        String normalized = normalize(status);
        return normalized.isBlank() ? AccessControl.STATUS_ACTIVE : normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class CredentialSnapshot {
        private final String username;
        private final String email;
        private final String password;

        private CredentialSnapshot(String username, String email, String password) {
            this.username = username;
            this.email = email;
            this.password = password;
        }
    }
}
