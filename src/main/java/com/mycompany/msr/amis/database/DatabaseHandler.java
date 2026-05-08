package com.mycompany.msr.amis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class DatabaseHandler {

    private static final SyncStorageRepository SYNC_STORAGE_REPOSITORY = new SyncStorageRepository();

    private static final String DATABASE_FILE_NAME = "msr_amis.db";
    private static final Path DATABASE_PATH = resolveDatabasePath();
    private static final String URL = "jdbc:sqlite:" + DATABASE_PATH;
    private static final String DEFAULT_DEPARTMENT = "MSR";
    private static final DateTimeFormatter RESET_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_USER_USERNAME = "user";

    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(URL);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    // ================= INIT DATABASE =================
    public static void initializeDatabase() {

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // EQUIPMENT
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS equipment (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "asset_code TEXT UNIQUE, " +
                            "name TEXT NOT NULL, " +
                            "category TEXT NOT NULL, " +
                            "serial_number TEXT UNIQUE NOT NULL, " +
                            "condition TEXT, " +
                            "source TEXT, " +
                            "entry_date TEXT DEFAULT (DATE('now')), " +
                            "status TEXT DEFAULT 'AVAILABLE' " +
                            "CHECK(status IN ('AVAILABLE','ASSIGNED','MAINTENANCE','RETIRED'))" +
                    ")"
            );

            // TRIGGER
            stmt.execute("DROP TRIGGER IF EXISTS generate_asset_code");
            stmt.execute(
                    "CREATE TRIGGER generate_asset_code " +
                            "AFTER INSERT ON equipment " +
                            "BEGIN " +
                            "UPDATE equipment SET asset_code = " +
                            "'MSR-' || " +
                            "substr(" +
                            "CASE " +
                            "WHEN length(replace(trim(upper(COALESCE(NEW.category, 'OTH'))), ' ', '')) >= 3 " +
                            "THEN replace(trim(upper(COALESCE(NEW.category, 'OTH'))), ' ', '') " +
                            "ELSE replace(trim(upper(COALESCE(NEW.category, 'OTH'))), ' ', '') || 'OTH' " +
                            "END, 1, 3" +
                            ") || '-' || printf('%03d', NEW.id) " +
                            "WHERE id = NEW.id; " +
                            "END;"
            );

            // ASSIGNMENTS
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS assignments (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "person TEXT, " +
                            "department TEXT, " +
                            "equipment_type TEXT, " +
                            "reason TEXT, " +
                            "quantity INTEGER, " +
                            "status TEXT DEFAULT 'ACTIVE', " +
                            "date TEXT DEFAULT (DATE('now'))" +
                    ")"
            );

            // USERS
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS users (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "full_name TEXT NOT NULL, " +
                            "username TEXT UNIQUE NOT NULL, " +
                            "password TEXT NOT NULL, " +
                            "role TEXT DEFAULT 'USER', " +
                            "status TEXT DEFAULT 'ACTIVE', " +
                            "temporary INTEGER NOT NULL DEFAULT 0 CHECK(temporary IN (0,1)), " +
                            "department TEXT, " +
                            "phone TEXT UNIQUE, " +
                            "email TEXT UNIQUE, " +
                            "reset_code TEXT, " +
                            "reset_expiry TEXT, " +
                            "reset_requested_at TEXT, " +
                            "last_password_reset TEXT, " +
                            "created_at TEXT DEFAULT CURRENT_TIMESTAMP" +
                    ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS departments (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "name TEXT UNIQUE NOT NULL" +
                    ")"
            );

            // ✅ DISTRIBUTION TABLE (correct placement)
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS distribution (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "asset_code TEXT, " +
                            "assignment_id INTEGER, " +
                            "assigned_to TEXT, " +
                            "phone TEXT, " +
                            "nid TEXT, " +
                            "outstanding_remarks TEXT, " +
                            "date TEXT DEFAULT (DATE('now')), " +
                            "returned INTEGER DEFAULT 0 " +
                            "CHECK(returned IN (0,1))" +
                    ")"
            );
            stmt.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS ux_distribution_active_asset_code_ci " +
                            "ON distribution (LOWER(TRIM(asset_code))) WHERE returned = 0"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS returns (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "asset_code TEXT NOT NULL, " +
                            "returned_by TEXT NOT NULL, " +
                            "phone TEXT, " +
                            "nid TEXT, " +
                            "condition TEXT, " +
                            "remarks TEXT, " +
                            "return_date TEXT DEFAULT (DATE('now'))" +
                    ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS password_reset_audit (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "user_id INTEGER, " +
                            "identifier TEXT, " +
                            "event_type TEXT NOT NULL, " +
                            "status TEXT NOT NULL, " +
                            "details TEXT, " +
                            "created_at TEXT DEFAULT CURRENT_TIMESTAMP" +
                    ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS audit_log (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "action TEXT NOT NULL, " +
                            "entity TEXT, " +
                            "entity_id TEXT, " +
                            "performed_by TEXT, " +
                            "details TEXT, " +
                            "action_time TEXT DEFAULT CURRENT_TIMESTAMP" +
                    ")"
            );

            SYNC_STORAGE_REPOSITORY.ensureSchema(conn);

            migrateDatabase(conn);
            insertDepartmentIfMissing(conn, DEFAULT_DEPARTMENT);
            ensureSystemAccounts(conn);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void migrateDatabase(Connection conn) throws SQLException {
        try {
            SYNC_STORAGE_REPOSITORY.ensureSchema(conn);
        } catch (Exception e) {
            throw new SQLException("Failed to initialize sync storage schema.", e);
        }
        ensureColumn(conn, "users", "department", "TEXT");
        ensureColumn(conn, "users", "status", "TEXT DEFAULT 'ACTIVE'");
        ensureColumn(conn, "users", "temporary", "INTEGER NOT NULL DEFAULT 0 CHECK(temporary IN (0,1))");
        ensureColumn(conn, "users", "last_login", "TEXT");
        ensureColumn(conn, "users", "reset_code", "TEXT");
        ensureColumn(conn, "users", "reset_expiry", "TEXT");
        ensureColumn(conn, "users", "reset_requested_at", "TEXT");
        ensureColumn(conn, "users", "last_password_reset", "TEXT");
        ensureColumn(conn, "assignments", "reason", "TEXT");
        ensureColumn(conn, "assignments", "status", "TEXT DEFAULT 'ACTIVE'");
        ensureColumn(conn, "distribution", "assigned_to", "TEXT");
        ensureColumn(conn, "distribution", "returned", "INTEGER DEFAULT 0 CHECK(returned IN (0,1))");
        ensureColumn(conn, "distribution", "date", "TEXT DEFAULT (DATE('now'))");
        ensureColumn(conn, "distribution", "outstanding_remarks", "TEXT");
        ensureColumn(conn, "returns", "remarks", "TEXT");
        ensureColumn(conn, "audit_log", "username", "TEXT");
        ensureColumn(conn, "audit_log", "module_name", "TEXT");

        try (Statement stmt = conn.createStatement()) {
            if (hasColumn(conn, "distribution", "name")) {
                stmt.executeUpdate(
                        "UPDATE distribution " +
                        "SET assigned_to = name " +
                        "WHERE assigned_to IS NULL AND name IS NOT NULL"
                );
            }
            stmt.executeUpdate(
                    "UPDATE distribution " +
                    "SET returned = 0 " +
                    "WHERE returned IS NULL"
            );
            stmt.executeUpdate(
                    "UPDATE users SET status = '" + AccessControl.STATUS_ACTIVE + "' " +
                    "WHERE status IS NULL OR TRIM(status) = ''"
            );
            stmt.executeUpdate(
                    "UPDATE users SET temporary = 0 " +
                    "WHERE temporary IS NULL"
            );
            stmt.executeUpdate(
                    "UPDATE users SET temporary = 1 " +
                    "WHERE LOWER(email) IN (" +
                    "LOWER('" + AccessControl.DEFAULT_ADMIN_EMAIL + "'), " +
                    "LOWER('" + AccessControl.DEFAULT_USER_EMAIL + "'), " +
                    "LOWER('" + AccessControl.PRIMARY_SUPER_ADMIN_EMAIL + "'))"
            );
            stmt.executeUpdate(
                    "UPDATE assignments SET status = '" + AccessControl.STATUS_ACTIVE + "' " +
                    "WHERE status IS NULL OR TRIM(status) = ''"
            );
            stmt.executeUpdate(
                    "INSERT OR IGNORE INTO departments(name) " +
                    "SELECT DISTINCT TRIM(department) FROM users " +
                    "WHERE department IS NOT NULL AND TRIM(department) <> ''"
            );
            if (hasColumn(conn, "audit_log", "username")) {
                stmt.executeUpdate(
                        "UPDATE audit_log SET username = performed_by " +
                        "WHERE (username IS NULL OR TRIM(username) = '') " +
                        "AND performed_by IS NOT NULL AND TRIM(performed_by) <> ''"
                );
            }
            if (hasColumn(conn, "audit_log", "module_name")) {
                stmt.executeUpdate(
                        "UPDATE audit_log SET module_name = entity " +
                        "WHERE (module_name IS NULL OR TRIM(module_name) = '') " +
                        "AND entity IS NOT NULL AND TRIM(entity) <> ''"
                );
            }
        }

        repairMisalignedReturnRows(conn);
        removeDuplicateDistributionRows(conn);
        removeSupersededReturnRows(conn);
        normalizeLegacyAssetCodes(conn);
    }

    private static void repairMisalignedReturnRows(Connection conn) throws SQLException {
        String fixReturnsSql =
                "UPDATE returns " +
                "SET phone = (" +
                "        SELECT d.phone FROM distribution d " +
                "        WHERE LOWER(TRIM(d.asset_code)) = LOWER(TRIM(returns.asset_code)) " +
                "        ORDER BY d.id DESC LIMIT 1" +
                "    ), " +
                "    nid = (" +
                "        SELECT d.nid FROM distribution d " +
                "        WHERE LOWER(TRIM(d.asset_code)) = LOWER(TRIM(returns.asset_code)) " +
                "        ORDER BY d.id DESC LIMIT 1" +
                "    ), " +
                "    condition = CASE " +
                "        WHEN condition IS NULL OR TRIM(condition) = '' THEN 'Returned' " +
                "        ELSE 'Returned' " +
                "    END " +
                "WHERE EXISTS (" +
                "    SELECT 1 FROM distribution d " +
                "    WHERE LOWER(TRIM(d.asset_code)) = LOWER(TRIM(returns.asset_code)) " +
                "      AND COALESCE(TRIM(returns.phone), '') = COALESCE(TRIM(d.assigned_to), '') " +
                "      AND COALESCE(TRIM(returns.nid), '') = COALESCE(TRIM(d.phone), '') " +
                "      AND COALESCE(TRIM(returns.condition), '') = COALESCE(TRIM(d.nid), '')" +
                ")";

        String fixEquipmentSql =
                "UPDATE equipment " +
                "SET condition = 'Returned' " +
                "WHERE EXISTS (" +
                "    SELECT 1 " +
                "    FROM returns r " +
                "    INNER JOIN distribution d ON LOWER(TRIM(d.asset_code)) = LOWER(TRIM(r.asset_code)) " +
                "    WHERE LOWER(TRIM(r.asset_code)) = LOWER(TRIM(equipment.asset_code)) " +
                "      AND COALESCE(TRIM(equipment.condition), '') = COALESCE(TRIM(d.nid), '')" +
                ")";

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(fixReturnsSql);
            stmt.executeUpdate(fixEquipmentSql);
        }
    }

    private static void removeDuplicateDistributionRows(Connection conn) throws SQLException {
        String sql =
                "DELETE FROM distribution AS older " +
                "WHERE EXISTS (" +
                "    SELECT 1 FROM distribution AS newer " +
                "    WHERE newer.id > older.id " +
                "      AND COALESCE(TRIM(newer.asset_code), '') = COALESCE(TRIM(older.asset_code), '') " +
                "      AND COALESCE(newer.assignment_id, 0) = COALESCE(older.assignment_id, 0) " +
                "      AND COALESCE(TRIM(newer.assigned_to), '') = COALESCE(TRIM(older.assigned_to), '') " +
                "      AND COALESCE(TRIM(newer.phone), '') = COALESCE(TRIM(older.phone), '') " +
                "      AND COALESCE(TRIM(newer.nid), '') = COALESCE(TRIM(older.nid), '') " +
                "      AND COALESCE(TRIM(newer.date), '') = COALESCE(TRIM(older.date), '') " +
                "      AND COALESCE(newer.returned, 0) = COALESCE(older.returned, 0)" +
                ")";

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private static void removeSupersededReturnRows(Connection conn) throws SQLException {
        String sql =
                "DELETE FROM returns AS placeholder " +
                "WHERE COALESCE(TRIM(placeholder.condition), '') = 'Returned' " +
                "  AND COALESCE(TRIM(placeholder.remarks), '') = '' " +
                "  AND EXISTS (" +
                "    SELECT 1 FROM returns AS actual " +
                "    WHERE actual.id > placeholder.id " +
                "      AND COALESCE(TRIM(actual.asset_code), '') = COALESCE(TRIM(placeholder.asset_code), '') " +
                "      AND COALESCE(TRIM(actual.returned_by), '') = COALESCE(TRIM(placeholder.returned_by), '') " +
                "      AND COALESCE(TRIM(actual.phone), '') = COALESCE(TRIM(placeholder.phone), '') " +
                "      AND COALESCE(TRIM(actual.nid), '') = COALESCE(TRIM(placeholder.nid), '') " +
                "      AND COALESCE(TRIM(actual.return_date), '') = COALESCE(TRIM(placeholder.return_date), '') " +
                "      AND (COALESCE(TRIM(actual.condition), '') <> 'Returned' OR COALESCE(TRIM(actual.remarks), '') <> '')" +
                ")";

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private static void normalizeLegacyAssetCodes(Connection conn) throws SQLException {
        class AssetCodeMapping {
            final String oldCode;
            final String newCode;

            AssetCodeMapping(String oldCode, String newCode) {
                this.oldCode = oldCode;
                this.newCode = newCode;
            }
        }

        List<AssetCodeMapping> mappings = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, asset_code, category FROM equipment ORDER BY id"
        ); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String oldCode = normalizedOptional(rs.getString("asset_code"));
                String newCode = buildAssetCode(rs.getString("category"), rs.getInt("id"));
                if (!oldCode.equalsIgnoreCase(newCode)) {
                    mappings.add(new AssetCodeMapping(oldCode, newCode));
                }
            }
        }

        for (AssetCodeMapping mapping : mappings) {
            try (PreparedStatement updateEquipment = conn.prepareStatement(
                    "UPDATE equipment SET asset_code = ? WHERE LOWER(TRIM(asset_code)) = LOWER(TRIM(?))"
            );
                 PreparedStatement updateDistribution = conn.prepareStatement(
                         "UPDATE distribution SET asset_code = ? WHERE LOWER(TRIM(asset_code)) = LOWER(TRIM(?))"
                 );
                 PreparedStatement updateReturns = conn.prepareStatement(
                         "UPDATE returns SET asset_code = ? WHERE LOWER(TRIM(asset_code)) = LOWER(TRIM(?))"
                 );
                 PreparedStatement updateAuditEntity = conn.prepareStatement(
                         "UPDATE audit_log SET entity_id = ? WHERE LOWER(COALESCE(TRIM(entity_id), '')) = LOWER(TRIM(?))"
                 );
                 PreparedStatement updateAuditDetails = conn.prepareStatement(
                         "UPDATE audit_log SET details = REPLACE(details, ?, ?) WHERE details LIKE '%' || ? || '%'"
                 )) {
                updateEquipment.setString(1, mapping.newCode);
                updateEquipment.setString(2, mapping.oldCode);
                updateEquipment.executeUpdate();

                updateDistribution.setString(1, mapping.newCode);
                updateDistribution.setString(2, mapping.oldCode);
                updateDistribution.executeUpdate();

                updateReturns.setString(1, mapping.newCode);
                updateReturns.setString(2, mapping.oldCode);
                updateReturns.executeUpdate();

                updateAuditEntity.setString(1, mapping.newCode);
                updateAuditEntity.setString(2, mapping.oldCode);
                updateAuditEntity.executeUpdate();

                updateAuditDetails.setString(1, mapping.oldCode);
                updateAuditDetails.setString(2, mapping.newCode);
                updateAuditDetails.setString(3, mapping.oldCode);
                updateAuditDetails.executeUpdate();
            }
        }
    }

    private static void ensureSystemAccounts(Connection conn) throws SQLException {
        ensurePrimarySuperAdmin(conn);
        ensureDefaultAdmin(conn);
        ensureDefaultUser(conn);
        demoteOtherSuperAdmins(conn);
    }

    private static void ensurePrimarySuperAdmin(Connection conn) throws SQLException {
        String lookupSql = "SELECT id FROM users WHERE LOWER(email) = LOWER(?)";
        String insertSql =
                "INSERT INTO users (full_name, username, password, role, status, temporary, department, phone, email) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String updateSql =
                "UPDATE users SET full_name=?, username=?, role=?, status=?, temporary=1, department=?, email=? " +
                "WHERE LOWER(email) = LOWER(?)";

        insertDepartmentIfMissing(conn, DEFAULT_DEPARTMENT);

        try (PreparedStatement lookup = conn.prepareStatement(lookupSql)) {
            lookup.setString(1, AccessControl.PRIMARY_SUPER_ADMIN_EMAIL);
            try (ResultSet rs = lookup.executeQuery()) {
                if (rs.next()) {
                    try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                        update.setString(1, "W Kautsa");
                        update.setString(2, AccessControl.PRIMARY_SUPER_ADMIN_EMAIL);
                        update.setString(3, AccessControl.ROLE_SUPER_ADMIN);
                        update.setString(4, AccessControl.STATUS_ACTIVE);
                        update.setString(5, DEFAULT_DEPARTMENT);
                        update.setString(6, AccessControl.PRIMARY_SUPER_ADMIN_EMAIL);
                        update.setString(7, AccessControl.PRIMARY_SUPER_ADMIN_EMAIL);
                        update.executeUpdate();
                    }
                    return;
                }
            }
        }

        try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
            insert.setString(1, "W Kautsa");
            insert.setString(2, AccessControl.PRIMARY_SUPER_ADMIN_EMAIL);
            insert.setString(3, PasswordUtils.hash("admin123"));
            insert.setString(4, AccessControl.ROLE_SUPER_ADMIN);
            insert.setString(5, AccessControl.STATUS_ACTIVE);
            insert.setInt(6, 1);
            insert.setString(7, DEFAULT_DEPARTMENT);
            insert.setString(8, null);
            insert.setString(9, AccessControl.PRIMARY_SUPER_ADMIN_EMAIL);
            insert.executeUpdate();
        }
    }

    private static void ensureDefaultAdmin(Connection conn) throws SQLException {
        String lookupSql = "SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?)";
        String insertSql =
                "INSERT INTO users (full_name, username, password, role, status, temporary, department, phone, email) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String updateSql =
                "UPDATE users SET full_name=?, username=?, role=?, status=?, temporary=1, department=?, email=? " +
                "WHERE LOWER(email) = LOWER(?)";

        String email = AccessControl.DEFAULT_ADMIN_EMAIL;

        try (PreparedStatement lookup = conn.prepareStatement(lookupSql)) {
            lookup.setString(1, email);
            try (ResultSet rs = lookup.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                        String currentStatus = getUserStatusByEmail(conn, email);
                        update.setString(1, "System Setup Administrator");
                        update.setString(2, DEFAULT_ADMIN_USERNAME);
                        update.setString(3, AccessControl.ROLE_ADMIN);
                        update.setString(4, currentStatus.isBlank() ? AccessControl.STATUS_ACTIVE : currentStatus);
                        update.setString(5, DEFAULT_DEPARTMENT);
                        update.setString(6, email);
                        update.setString(7, email);
                        update.executeUpdate();
                    }
                    return;
                }
            }
        }

        String password = PasswordUtils.hash("admin123");

        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            insertDepartmentIfMissing(conn, DEFAULT_DEPARTMENT);
            ps.setString(1, "System Setup Administrator");
            ps.setString(2, DEFAULT_ADMIN_USERNAME);
            ps.setString(3, password);
            ps.setString(4, AccessControl.ROLE_ADMIN);
            ps.setString(5, AccessControl.STATUS_ACTIVE);
            ps.setInt(6, 1);
            ps.setString(7, DEFAULT_DEPARTMENT);
            ps.setString(8, null);
            ps.setString(9, email);
            ps.executeUpdate();
        }
    }

    private static void ensureDefaultUser(Connection conn) throws SQLException {
        String lookupSql = "SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?)";
        String insertSql =
                "INSERT INTO users (full_name, username, password, role, status, temporary, department, phone, email) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String updateSql =
                "UPDATE users SET full_name=?, username=?, role=?, status=?, temporary=1, department=?, email=? " +
                "WHERE LOWER(email) = LOWER(?)";
        String email = AccessControl.DEFAULT_USER_EMAIL;

        try (PreparedStatement lookup = conn.prepareStatement(lookupSql)) {
            lookup.setString(1, email);
            try (ResultSet rs = lookup.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                        String currentStatus = getUserStatusByEmail(conn, email);
                        update.setString(1, "System Standard User");
                        update.setString(2, DEFAULT_USER_USERNAME);
                        update.setString(3, AccessControl.ROLE_USER);
                        update.setString(4, currentStatus.isBlank() ? AccessControl.STATUS_ACTIVE : currentStatus);
                        update.setString(5, DEFAULT_DEPARTMENT);
                        update.setString(6, email);
                        update.setString(7, email);
                        update.executeUpdate();
                    }
                    return;
                }
            }
        }

        String password = PasswordUtils.hash("admin123");

        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            insertDepartmentIfMissing(conn, DEFAULT_DEPARTMENT);
            ps.setString(1, "System Standard User");
            ps.setString(2, DEFAULT_USER_USERNAME);
            ps.setString(3, password);
            ps.setString(4, AccessControl.ROLE_USER);
            ps.setString(5, AccessControl.STATUS_ACTIVE);
            ps.setInt(6, 1);
            ps.setString(7, DEFAULT_DEPARTMENT);
            ps.setString(8, null);
            ps.setString(9, email);
            ps.executeUpdate();
        }
    }

    private static void demoteOtherSuperAdmins(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET role = ? " +
                "WHERE UPPER(role) = 'SUPER_ADMIN' " +
                "AND LOWER(email) <> LOWER(?) " +
                "AND LOWER(email) <> LOWER(?)"
        )) {
            ps.setString(1, AccessControl.ROLE_ADMIN);
            ps.setString(2, AccessControl.PRIMARY_SUPER_ADMIN_EMAIL);
            ps.setString(3, AccessControl.DEFAULT_ADMIN_EMAIL);
            ps.executeUpdate();
        }
    }

    private static String getUserStatusByEmail(Connection conn, String email) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(status, '') FROM users WHERE LOWER(email) = LOWER(?) LIMIT 1"
        )) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return normalizedOptional(rs.getString(1));
                }
            }
        }
        return "";
    }

    private static void ensureColumn(Connection conn, String tableName, String columnName, String definition)
            throws SQLException {
        if (hasColumn(conn, tableName, columnName)) {
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    private static boolean hasColumn(Connection conn, String tableName, String columnName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(" + tableName + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    // ================= EQUIPMENT =================
    public static void insertEquipment(Equipment eq) throws Exception {
        String name = normalizedRequired(eq.getName(), "Equipment name is required.");
        String category = normalizeEquipmentCategory(eq.getCategory());
        String serialNumber = normalizedRequired(eq.getSerialNumber(), "IMEI/Serial number is required.");
        String condition = normalizedOptional(eq.getCondition());
        String source = normalizedOptional(eq.getSource());
        String entryDate = normalizedOptional(eq.getEntryDate());

        try (Connection conn = getConnection()) {
            ensureEquipmentIdentifierAvailable(conn, serialNumber, null);
        }

        String sql = "INSERT INTO equipment " +
                "(name, category, serial_number, condition, source, entry_date) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        String savedAssetCode;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, category);
            ps.setString(3, serialNumber);
            ps.setString(4, condition);
            ps.setString(5, source);
            ps.setString(6, entryDate.isBlank() ? Equipment.today() : entryDate);

            ps.executeUpdate();
            savedAssetCode = findAssetCodeBySerial(conn, serialNumber);
        }
        logAudit(
                "ADD_EQUIPMENT",
                "EQUIPMENT",
                savedAssetCode,
                "Equipment added: " + name + ", category: " + category + ", serial: " + serialNumber
        );
    }

    public static void updateEquipment(String assetCode, String serialNumber, String name, String category, String condition)
            throws Exception {
        String normalizedAssetCode = normalizedRequired(assetCode, "Asset code is required.");
        String normalizedSerialNumber = normalizedRequired(serialNumber, "IMEI/Serial number is required.");
        String normalizedName = normalizedRequired(name, "Equipment name is required.");
        String normalizedCategory = normalizeEquipmentCategory(category);
        String normalizedCondition = normalizedOptional(condition);

        try (Connection conn = getConnection()) {
            ensureEquipmentIdentifierAvailable(conn, normalizedSerialNumber, normalizedAssetCode);
            String oldSnapshot = findEquipmentSnapshot(conn, normalizedAssetCode);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE equipment SET serial_number=?, name=?, category=?, condition=? WHERE asset_code=?"
            )) {
                ps.setString(1, normalizedSerialNumber);
                ps.setString(2, normalizedName);
                ps.setString(3, normalizedCategory);
                ps.setString(4, normalizedCondition);
                ps.setString(5, normalizedAssetCode);

                if (ps.executeUpdate() == 0) {
                    throw new Exception("Equipment record was not found.");
                }
            }
            logAudit(
                    "EDIT_EQUIPMENT",
                    "EQUIPMENT",
                    normalizedAssetCode,
                    "Equipment edited. Old: " + oldSnapshot + ". New: name=" + normalizedName +
                            ", category=" + normalizedCategory + ", serial=" + normalizedSerialNumber +
                            ", condition=" + normalizedCondition
            );
        }
    }

    public static void deleteEquipment(String assetCode) throws Exception {
        String normalizedAssetCode = normalizedRequired(assetCode, "Asset code is required.");

        try (Connection conn = getConnection()) {
            if (hasDistributionHistory(conn, normalizedAssetCode)) {
                throw new Exception("Equipment with assignment or return history cannot be deleted.");
            }
            String oldSnapshot = findEquipmentSnapshot(conn, normalizedAssetCode);

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM equipment WHERE asset_code=?")) {
                ps.setString(1, normalizedAssetCode);
                if (ps.executeUpdate() == 0) {
                    throw new Exception("Equipment record was not found.");
                }
            }
            logAudit("DELETE_EQUIPMENT", "EQUIPMENT", normalizedAssetCode, "Equipment deleted. Old: " + oldSnapshot);
        }
    }

    public static String insertReplacementEquipment(String equipmentType, String serialNumber, String source) throws Exception {
        String normalizedSerialNumber = normalizedRequired(serialNumber, "IMEI/Serial number is required.");
        String insertSql = "INSERT INTO equipment (name, category, serial_number, condition, source, entry_date) VALUES (?, ?, ?, ?, ?, DATE('now'))";
        String selectSql = "SELECT asset_code FROM equipment WHERE serial_number = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement insert = conn.prepareStatement(insertSql);
                 PreparedStatement select = conn.prepareStatement(selectSql)) {
                ensureEquipmentIdentifierAvailable(conn, normalizedSerialNumber, null);

                insert.setString(1, equipmentType);
                insert.setString(2, equipmentType);
                insert.setString(3, normalizedSerialNumber);
                insert.setString(4, "New");
                insert.setString(5, source);
                insert.executeUpdate();

                select.setString(1, normalizedSerialNumber);
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        String assetCode = rs.getString("asset_code");
                        conn.commit();
                        return assetCode;
                    }
                }

                throw new Exception("Replacement equipment was saved but asset code could not be resolved.");
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public static boolean equipmentIdentifierExists(String identifier) {
        String normalizedIdentifier = normalizedOptional(identifier);
        if (normalizedIdentifier.isBlank()) {
            return false;
        }

        String sql = "SELECT COUNT(*) FROM equipment " +
                "WHERE LOWER(TRIM(asset_code)) = LOWER(TRIM(?)) " +
                "OR LOWER(TRIM(serial_number)) = LOWER(TRIM(?))";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizedIdentifier);
            ps.setString(2, normalizedIdentifier);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to check equipment identifier.", e);
        }
    }

    public static boolean wasAssetReturnedForAssignment(int assignmentId, String assetCode) {
        String normalizedAssetCode = normalizedOptional(assetCode);
        if (normalizedAssetCode.isBlank()) {
            return false;
        }

        String sql = "SELECT COUNT(*) FROM distribution " +
                "WHERE assignment_id = ? " +
                "AND LOWER(TRIM(asset_code)) = LOWER(TRIM(?)) " +
                "AND returned = 1";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, assignmentId);
            ps.setString(2, normalizedAssetCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to check returned asset for assignment.", e);
        }
    }

    private static void ensureEquipmentIdentifierAvailable(Connection conn, String identifier, String currentAssetCode)
            throws Exception {
        String normalizedIdentifier = normalizedRequired(identifier, "IMEI/Serial number is required.");
        String normalizedCurrentAssetCode = normalizedOptional(currentAssetCode);
        String sql = "SELECT asset_code, serial_number FROM equipment " +
                "WHERE (LOWER(TRIM(asset_code)) = LOWER(TRIM(?)) " +
                "OR LOWER(TRIM(serial_number)) = LOWER(TRIM(?))) " +
                "AND (? = '' OR LOWER(TRIM(asset_code)) <> LOWER(TRIM(?))) " +
                "LIMIT 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizedIdentifier);
            ps.setString(2, normalizedIdentifier);
            ps.setString(3, normalizedCurrentAssetCode);
            ps.setString(4, normalizedCurrentAssetCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new Exception(
                            "Equipment identifier already exists. Asset Code: " + rs.getString("asset_code") +
                                    ", Serial/IMEI: " + rs.getString("serial_number")
                    );
                }
            }
        }
    }

    public static ObservableList<Equipment> getAllEquipment() {

        ObservableList<Equipment> list = FXCollections.observableArrayList();

        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT * FROM equipment ORDER BY entry_date DESC, id DESC"
             )) {

            while (rs.next()) {
                list.add(new Equipment(
                        rs.getInt("id"),
                        rs.getString("asset_code"),
                        rs.getString("serial_number"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getString("condition"),
                        rs.getString("source"),
                        rs.getString("entry_date"),
                        rs.getString("status")
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    // ================= ASSIGNMENTS =================
    public static void insertAssignment(String person, String dept, String type, String reason, int qty) throws Exception {

        String sql = "INSERT INTO assignments (person, department, equipment_type, reason, quantity, status, date) VALUES (?, ?, ?, ?, ?, ?, DATE('now'))";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, person);
            ps.setString(2, dept);
            ps.setString(3, type);
            ps.setString(4, reason);
            ps.setInt(5, qty);
            ps.setString(6, AccessControl.STATUS_ACTIVE);

            ps.executeUpdate();
            String id = "";
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    id = Integer.toString(keys.getInt(1));
                }
            }
            logAudit(
                    "CREATE_ASSIGNMENT",
                    "ASSIGNMENTS",
                    id,
                    "Assignment created for " + person + ". Department: " + dept +
                            ", equipment: " + type + ", quantity: " + qty + ", reason: " + reason
            );
        }
    }

    public static void deleteAssignment(int id) throws Exception {
        try (Connection conn = getConnection()) {
            requireAssignmentMutable(conn, id);
            if (hasAssignmentDistributionHistory(conn, id)) {
                throw new Exception("Assignments that already have distributed equipment cannot be deleted.");
            }
            String oldSnapshot = findAssignmentSnapshot(conn, id);

            String sql = "DELETE FROM assignments WHERE id=?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, id);
                if (ps.executeUpdate() == 0) {
                    throw new Exception("Assignment record was not found.");
                }
            }
            logAudit("DELETE_ASSIGNMENT", "ASSIGNMENTS", Integer.toString(id), "Assignment deleted. Old: " + oldSnapshot);
        }
    }

    public static void updateAssignment(int id, String person, String dept, String type, String reason, int qty) throws Exception {
        try (Connection conn = getConnection()) {
            requireAssignmentMutable(conn, id);
            int distributed = getDistributedCountForAssignment(conn, id);
            if (distributed > 0) {
                throw new Exception("This assignment already has distributed equipment and can no longer be edited.");
            }

            String normalizedPerson = normalizedRequired(person, "Responsible person is required.");
            String normalizedDepartment = normalizedRequired(dept, "Department is required.");
            String normalizedType = normalizeEquipmentCategory(type);
            String normalizedReason = normalizedRequired(reason, "Reason is required.");
            if (qty <= 0) {
                throw new Exception("Quantity must be greater than zero.");
            }

            int available = getAvailableStock(conn, normalizedType);
            if (qty > available) {
                throw new Exception("Only " + available + " " + normalizedType + " item(s) are currently available.");
            }
            String oldSnapshot = findAssignmentSnapshot(conn, id);

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE assignments SET person=?, department=?, equipment_type=?, reason=?, quantity=? WHERE id=?"
            )) {
                ps.setString(1, normalizedPerson);
                ps.setString(2, normalizedDepartment);
                ps.setString(3, normalizedType);
                ps.setString(4, normalizedReason);
                ps.setInt(5, qty);
                ps.setInt(6, id);

                if (ps.executeUpdate() == 0) {
                    throw new Exception("Assignment record was not found.");
                }
            }
            logAudit(
                    "EDIT_ASSIGNMENT",
                    "ASSIGNMENTS",
                    Integer.toString(id),
                    "Assignment edited. Old: " + oldSnapshot + ". New: person=" + normalizedPerson +
                            ", department=" + normalizedDepartment + ", equipment=" + normalizedType +
                            ", quantity=" + qty + ", reason=" + normalizedReason
            );
        }
    }

    public static List<Assignment> getAssignments() {

        List<Assignment> list = new ArrayList<>();

        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM assignments")) {

            while (rs.next()) {
                list.add(new Assignment(
                        rs.getInt("id"),
                        rs.getString("person"),
                        rs.getString("department"),
                        rs.getString("equipment_type"),
                        rs.getString("reason"),
                        rs.getInt("quantity"),
                        rs.getString("date")
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    public static List<Assignment> getAssignmentsPendingReturn() {
        List<Assignment> list = new ArrayList<>();

        String sql =
                "SELECT DISTINCT a.id, a.person, a.department, a.equipment_type, a.reason, a.quantity, a.date " +
                "FROM assignments a " +
                "INNER JOIN distribution d ON d.assignment_id = a.id " +
                "WHERE d.returned = 0 " +
                "ORDER BY a.date DESC, a.id DESC";

        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                list.add(new Assignment(
                        rs.getInt("id"),
                        rs.getString("person"),
                        rs.getString("department"),
                        rs.getString("equipment_type"),
                        rs.getString("reason"),
                        rs.getInt("quantity"),
                        rs.getString("date")
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    // ================= USERS =================
    public static ObservableList<User> getUsers() {

        ObservableList<User> list = FXCollections.observableArrayList();

        String sql = "SELECT * FROM users WHERE COALESCE(temporary, 0) = 0";
        if (Session.isSetupMode()) {
            sql += " AND UPPER(role) IN ('ADMIN', 'USER')";
        } else if (Session.hasRole(AccessControl.ROLE_ADMIN) && !Session.hasRole(AccessControl.ROLE_SUPER_ADMIN)) {
            sql += " AND UPPER(role) IN ('ADMIN', 'USER')";
        } else if (Session.hasRole(AccessControl.ROLE_USER)) {
            sql += " AND UPPER(role) = 'USER'";
        }
        sql += " ORDER BY full_name";

        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                list.add(new User(
                        rs.getInt("id"),
                        rs.getString("full_name"),
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getString("role"),
                        rs.getString("department"),
                        rs.getString("phone"),
                        rs.getString("email"),
                        rs.getString("status")
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    public static User authenticateUser(String email, String plainPassword) {
        String sql = "SELECT * FROM users WHERE LOWER(email) = LOWER(?) OR LOWER(username) = LOWER(?) LIMIT 1";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            ps.setString(2, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    AuditService.log(normalizedOptional(email), "LOGIN_FAILED", "AUTH", "Unknown login identifier.");
                    return null;
                }

                String status = normalizedOptional(rs.getString("status"));
                if (AccessControl.STATUS_FROZEN.equalsIgnoreCase(status)) {
                    AuditService.log(resolveAuditUsername(rs), "LOGIN_FAILED", "AUTH", "Frozen account login blocked.");
                    throw new SecurityException("This account is frozen. Contact an administrator.");
                }

                String storedPassword = rs.getString("password");
                if (!PasswordUtils.verify(plainPassword, storedPassword)) {
                    AuditService.log(resolveAuditUsername(rs), "LOGIN_FAILED", "AUTH", "Invalid password.");
                    return null;
                }

                try (PreparedStatement updateLastLogin = conn.prepareStatement(
                        "UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE id = ?"
                )) {
                    updateLastLogin.setInt(1, rs.getInt("id"));
                    updateLastLogin.executeUpdate();
                }
                AuditService.log(resolveAuditUsername(rs), "LOGIN_SUCCESS", "AUTH", "User logged in successfully.");

                return new User(
                        rs.getInt("id"),
                        rs.getString("full_name"),
                        rs.getString("username"),
                        storedPassword,
                        rs.getString("role"),
                        rs.getString("department"),
                        rs.getString("phone"),
                        rs.getString("email"),
                        rs.getString("status")
                );
            }

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean userExistsByEmail(String email) {
        return exists("SELECT COUNT(*) FROM users WHERE LOWER(email)=LOWER(?)", email);
    }

    public static boolean requiresInitialPasswordChange(User user) {
        if (user == null) {
            return false;
        }
        if (!AccessControl.DEFAULT_ADMIN_EMAIL.equalsIgnoreCase(user.getEmail())
                && !AccessControl.DEFAULT_USER_EMAIL.equalsIgnoreCase(user.getEmail())) {
            return false;
        }

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COALESCE(last_password_reset, '') FROM users WHERE LOWER(email) = LOWER(?) LIMIT 1"
             )) {
            ps.setString(1, user.getEmail());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && normalizedOptional(rs.getString(1)).isBlank();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String issuePasswordResetCode(String identifier, String resetCode, LocalDateTime expiresAt) throws Exception {
        String normalizedIdentifier = normalizedRequired(identifier, "Email or username is required.");
        String normalizedCode = normalizedRequired(resetCode, "Reset code is required.");

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                UserResetTarget target = findResetTarget(conn, normalizedIdentifier);
                LocalDateTime now = LocalDateTime.now();
                if (target.resetRequestedAt != null && target.resetRequestedAt.plusMinutes(1).isAfter(now)) {
                    logPasswordResetEvent(conn, target.id, normalizedIdentifier, "REQUEST_PASSWORD_RESET", "FAILED",
                            "Reset requested too frequently.");
                    throw new Exception("A reset code was already requested recently. Wait one minute and try again.");
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET reset_code=?, reset_expiry=?, reset_requested_at=? WHERE id=?"
                )) {
                    ps.setString(1, normalizedCode);
                    ps.setString(2, formatResetTimestamp(expiresAt));
                    ps.setString(3, formatResetTimestamp(now));
                    ps.setInt(4, target.id);
                    ps.executeUpdate();
                }

                logPasswordResetEvent(conn, target.id, normalizedIdentifier, "REQUEST_PASSWORD_RESET", "SUCCESS",
                        "Reset code issued to registered email.");
                conn.commit();
                return target.email;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static boolean isTemporarySetupAccount(User user) {
        return user != null && AccessControl.isTemporarySetupAccountEmail(user.getEmail());
    }

    public static void resetPasswordWithCode(String identifier, String resetCode, String hashedPassword) throws Exception {
        String normalizedIdentifier = normalizedRequired(identifier, "Email or username is required.");
        String normalizedCode = normalizedRequired(resetCode, "Reset code is required.");
        String normalizedPassword = normalizedRequired(hashedPassword, "Password is required.");

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                UserResetTarget target = findResetTarget(conn, normalizedIdentifier);
                if (target.resetCode == null || target.resetCode.isBlank()) {
                    logPasswordResetEvent(conn, target.id, normalizedIdentifier, "VERIFY_RESET_CODE", "FAILED",
                            "No active reset code.");
                    throw new Exception("No active reset code was found. Request a new code.");
                }

                if (!target.resetCode.equals(normalizedCode)) {
                    logPasswordResetEvent(conn, target.id, normalizedIdentifier, "VERIFY_RESET_CODE", "FAILED",
                            "Invalid reset code.");
                    throw new Exception("Invalid reset code.");
                }

                if (target.resetExpiry == null || target.resetExpiry.isBefore(LocalDateTime.now())) {
                    clearResetFields(conn, target.id);
                    logPasswordResetEvent(conn, target.id, normalizedIdentifier, "VERIFY_RESET_CODE", "FAILED",
                            "Expired reset code.");
                    throw new Exception("The reset code has expired. Request a new code.");
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET password=?, reset_code=NULL, reset_expiry=NULL, reset_requested_at=NULL, last_password_reset=? WHERE id=?"
                )) {
                    ps.setString(1, normalizedPassword);
                    ps.setString(2, formatResetTimestamp(LocalDateTime.now()));
                    ps.setInt(3, target.id);
                    ps.executeUpdate();
                }

                logPasswordResetEvent(conn, target.id, normalizedIdentifier, "RESET_PASSWORD_SUCCESS", "SUCCESS",
                        "Password reset completed.");
                AuditService.log(target.email, "PASSWORD_RESET_SUCCESS", "AUTH", "Password reset completed.");
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static void clearPasswordResetCode(String identifier) {
        String normalizedIdentifier = normalizedOptional(identifier);
        if (normalizedIdentifier.isBlank()) {
            return;
        }

        try (Connection conn = getConnection()) {
            UserResetTarget target = findResetTarget(conn, normalizedIdentifier);
            clearResetFields(conn, target.id);
            logPasswordResetEvent(conn, target.id, normalizedIdentifier, "REQUEST_PASSWORD_RESET", "FAILED",
                    "Reset code cleared after email delivery failure.");
        } catch (Exception ignored) {
            // Best-effort cleanup only.
        }
    }

    public static boolean resetUserPasswordByEmail(String email, String hashedPassword) {
        String sql = "UPDATE users SET password=? WHERE LOWER(email)=LOWER(?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, hashedPassword);
            ps.setString(2, email);
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void insertUser(String name, String password,
                                  String role, String department, String email) throws Exception {
        if (!Session.isSetupMode()) {
            AccessControl.requireRole(AccessControl.ROLE_SUPER_ADMIN, AccessControl.ROLE_ADMIN);
        }
        if (AccessControl.isPrimarySuperAdminEmail(email)) {
            throw new SecurityException("This email is reserved for the primary Super Admin account.");
        }
        if (AccessControl.isTemporarySetupAccountEmail(email)) {
            throw new SecurityException("This email is reserved for the temporary setup account.");
        }
        if (!AccessControl.canAssignRole(role)) {
            throw new SecurityException("You are not allowed to assign the " + role + " role.");
        }

        String sql =
                "INSERT INTO users (full_name, username, password, role, status, temporary, department, phone, email) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            insertDepartmentIfMissing(conn, department);

            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, password);
            ps.setString(4, role);
            ps.setString(5, AccessControl.STATUS_ACTIVE);
            ps.setInt(6, 0);
            ps.setString(7, department);
            ps.setString(8, null);
            ps.setString(9, email);

            ps.executeUpdate();
        }
        logAudit(
                "CREATE_USER",
                "USERS",
                email,
                "User created: " + name + ", role: " + role + ", department: " + department
        );
    }

    public static boolean emailExists(String email) {
        return exists("SELECT COUNT(*) FROM users WHERE email=?", email);
    }

    public static boolean emailExistsForOtherUser(String email, int userId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM users WHERE email=? AND id<>?"
             )) {

            ps.setString(1, email);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean phoneExists(String phone) {
        return exists("SELECT COUNT(*) FROM users WHERE phone=?", phone);
    }

    public static int getAdminCount() {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM users WHERE UPPER(role) = 'ADMIN' AND UPPER(COALESCE(status, 'ACTIVE')) = 'ACTIVE'"
             )) {
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static int countActivePermanentUsersByRole(Connection conn, String role) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM users " +
                        "WHERE UPPER(role) = UPPER(?) " +
                        "AND UPPER(COALESCE(status, 'ACTIVE')) = 'ACTIVE' " +
                        "AND COALESCE(temporary, 0) = 0"
        )) {
            ps.setString(1, normalizedOptional(role));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static boolean exists(String sql, String value) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, value);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static UserResetTarget findResetTarget(Connection conn, String identifier) throws Exception {
        String sql = "SELECT id, username, email, reset_code, reset_expiry, reset_requested_at " +
                "FROM users WHERE LOWER(email) = LOWER(?) OR LOWER(username) = LOWER(?) LIMIT 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    logPasswordResetEvent(conn, null, identifier, "REQUEST_PASSWORD_RESET", "FAILED",
                            "Account not found.");
                    throw new Exception("No account was found for that email or username.");
                }

                String email = normalizedOptional(rs.getString("email"));
                if (email.isBlank()) {
                    logPasswordResetEvent(conn, rs.getInt("id"), identifier, "REQUEST_PASSWORD_RESET", "FAILED",
                            "Account has no registered email.");
                    throw new Exception("This account has no registered email. Contact an administrator.");
                }

                return new UserResetTarget(
                        rs.getInt("id"),
                        normalizedOptional(rs.getString("username")),
                        email,
                        normalizedOptional(rs.getString("reset_code")),
                        parseResetTimestamp(rs.getString("reset_expiry")),
                        parseResetTimestamp(rs.getString("reset_requested_at"))
                );
            }
        }
    }

    private static void clearResetFields(Connection conn, int userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET reset_code=NULL, reset_expiry=NULL, reset_requested_at=NULL WHERE id=?"
        )) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    public static void completeTemporarySetup(String replacementEmail) throws Exception {
        User currentUser = Session.getCurrentUser();
        String actorEmail = currentUser == null ? AccessControl.DEFAULT_ADMIN_EMAIL : normalizedOptional(currentUser.getEmail());
        if (!AccessControl.isTemporarySetupAccountEmail(actorEmail)) {
            actorEmail = AccessControl.DEFAULT_ADMIN_EMAIL;
        }
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (countActivePermanentUsersByRole(conn, AccessControl.ROLE_ADMIN) < 1) {
                    throw new Exception("Create at least one permanent ADMIN account before finishing setup.");
                }
                if (countActivePermanentUsersByRole(conn, AccessControl.ROLE_USER) < 1) {
                    throw new Exception("Create at least one permanent USER account before finishing setup.");
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET status=?, reset_code=NULL, reset_expiry=NULL, reset_requested_at=NULL " +
                                "WHERE LOWER(email) IN (LOWER(?), LOWER(?))"
                )) {
                    ps.setString(1, AccessControl.STATUS_FROZEN);
                    ps.setString(2, AccessControl.DEFAULT_ADMIN_EMAIL);
                    ps.setString(3, AccessControl.DEFAULT_USER_EMAIL);
                    ps.executeUpdate();
                }
                AuditService.log(
                        actorEmail,
                        "TEMPORARY_SETUP_COMPLETED",
                        "USERS",
                        "Temporary admin and user accounts frozen after creating permanent access for "
                                + normalizedOptional(replacementEmail)
                );
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public static void completeInitialPasswordChange(String email, String hashedPassword) throws Exception {
        String normalizedEmail = normalizedRequired(email, "Email is required.");
        String normalizedPassword = normalizedRequired(hashedPassword, "Password is required.");

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE users SET password=?, last_password_reset=? WHERE LOWER(email)=LOWER(?)"
             )) {
            ps.setString(1, normalizedPassword);
            ps.setString(2, formatResetTimestamp(LocalDateTime.now()));
            ps.setString(3, normalizedEmail);

            if (ps.executeUpdate() == 0) {
                throw new Exception("User account not found.");
            }
        }
        AuditService.log(normalizedEmail, "INITIAL_PASSWORD_CHANGED", "AUTH", "Initial password changed successfully.");
    }

    private static void logPasswordResetEvent(Connection conn, Integer userId, String identifier,
                                              String eventType, String status, String details) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO password_reset_audit (user_id, identifier, event_type, status, details) VALUES (?, ?, ?, ?, ?)"
        )) {
            if (userId == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, userId);
            }
            ps.setString(2, normalizedOptional(identifier));
            ps.setString(3, eventType);
            ps.setString(4, status);
            ps.setString(5, normalizedOptional(details));
            ps.executeUpdate();
        }
    }

    private static LocalDateTime parseResetTimestamp(String value) {
        String normalized = normalizedOptional(value);
        if (normalized.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(normalized, RESET_TIMESTAMP_FORMAT);
    }

    private static String formatResetTimestamp(LocalDateTime value) {
        return value == null ? "" : value.format(RESET_TIMESTAMP_FORMAT);
    }

    public static void deleteUser(int id) throws Exception {
        AccessControl.requireRole(AccessControl.ROLE_SUPER_ADMIN, AccessControl.ROLE_ADMIN);
        User target = getUserById(id);
        if (!AccessControl.canManageTarget(target)) {
            throw new SecurityException("You are not allowed to delete this user.");
        }
        if (target != null
                && AccessControl.ROLE_SUPER_ADMIN.equalsIgnoreCase(target.getRole())
                && getRoleCount(AccessControl.ROLE_SUPER_ADMIN) <= 1) {
            throw new SecurityException("The last Super Admin account cannot be deleted.");
        }
        if (target != null
                && AccessControl.ROLE_ADMIN.equalsIgnoreCase(target.getRole())
                && getAdminCount() <= 1) {
            throw new SecurityException("The last active admin account cannot be deleted.");
        }

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id=?")) {

            ps.setInt(1, id);
            if (ps.executeUpdate() == 0) {
                throw new Exception("User account not found.");
            }
        }
        logAudit(
                "DELETE_USER",
                "USERS",
                Integer.toString(id),
                "User deleted: " + (target == null ? id : target.getEmail()) +
                        ", role: " + (target == null ? "" : target.getRole())
        );
    }

    public static boolean updateUser(int id, String name, String password, String role, String department, String email) throws Exception {
        AccessControl.requireRole(AccessControl.ROLE_SUPER_ADMIN, AccessControl.ROLE_ADMIN);
        User target = getUserById(id);
        if (!AccessControl.canManageTarget(target)) {
            throw new SecurityException("You are not allowed to edit this user.");
        }
        if (!AccessControl.canAssignRole(role)) {
            throw new SecurityException("You are not allowed to assign the " + role + " role.");
        }
        if (target != null
                && AccessControl.ROLE_SUPER_ADMIN.equalsIgnoreCase(target.getRole())
                && !AccessControl.ROLE_SUPER_ADMIN.equalsIgnoreCase(role)
                && getRoleCount(AccessControl.ROLE_SUPER_ADMIN) <= 1) {
            throw new SecurityException("The last Super Admin account cannot be demoted.");
        }
        if (target != null
                && AccessControl.ROLE_ADMIN.equalsIgnoreCase(target.getRole())
                && !AccessControl.ROLE_ADMIN.equalsIgnoreCase(role)
                && getAdminCount() <= 1) {
            throw new SecurityException("The last active admin account cannot be changed to another role.");
        }
        String sql;

        // A real edit must update the selected database row by ID.
        // Without WHERE id = ?, the save action may appear to work in the dialog
        // while nothing meaningful is persisted to the actual record.
        if (password == null || password.isBlank()) {
            sql = "UPDATE users SET full_name=?, username=?, role=?, department=?, email=? WHERE id=?";
        } else {
            sql = "UPDATE users SET full_name=?, username=?, password=?, role=?, department=?, email=? WHERE id=?";
        }

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            insertDepartmentIfMissing(conn, department);

            ps.setString(1, name);
            ps.setString(2, email);

            if (password == null || password.isBlank()) {
                ps.setString(3, role);
                ps.setString(4, department);
                ps.setString(5, email);
                ps.setInt(6, id);
            } else {
                ps.setString(3, password);
                ps.setString(4, role);
                ps.setString(5, department);
                ps.setString(6, email);
                ps.setInt(7, id);
            }

            boolean updated = ps.executeUpdate() > 0;
            if (updated) {
                logAudit(
                        "EDIT_USER",
                        "USERS",
                        Integer.toString(id),
                        "User edited: " + email + ", role: " + role + ", department: " + department +
                                (password == null || password.isBlank() ? "" : ". Password changed.")
                );
            }
            return updated;
        }
    }

    public static boolean updateUserStatus(int id, String status) throws Exception {
        AccessControl.requireRole(AccessControl.ROLE_SUPER_ADMIN, AccessControl.ROLE_ADMIN);
        User target = getUserById(id);
        if (!AccessControl.canManageTarget(target)) {
            throw new SecurityException("You are not allowed to change this user's status.");
        }
        if (target != null
                && AccessControl.ROLE_SUPER_ADMIN.equalsIgnoreCase(target.getRole())
                && AccessControl.STATUS_FROZEN.equalsIgnoreCase(status)
                && getRoleCount(AccessControl.ROLE_SUPER_ADMIN) <= 1) {
            throw new SecurityException("The last Super Admin account cannot be frozen.");
        }

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE users SET status=? WHERE id=?")) {
            ps.setString(1, status);
            ps.setInt(2, id);
            boolean updated = ps.executeUpdate() > 0;
            if (updated) {
                logAudit(
                        "USER_" + normalizedOptional(status).toUpperCase(),
                        "USERS",
                        Integer.toString(id),
                        "User status changed to " + normalizedOptional(status).toUpperCase()
                );
            }
            return updated;
        }
    }

    public static int getRoleCount(String role) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM users WHERE UPPER(role) = UPPER(?)"
             )) {
            ps.setString(1, role);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static User getUserById(int id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("full_name"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("role"),
                            rs.getString("department"),
                            rs.getString("phone"),
                            rs.getString("email"),
                            rs.getString("status")
                    );
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void logAudit(String action, String entity, String entityId, String details) {
        User currentUser = Session.getCurrentUser();
        String performedBy = currentUser == null
                ? "SYSTEM"
                : (currentUser.getEmail() == null || currentUser.getEmail().isBlank()
                ? currentUser.getUsername()
                : currentUser.getEmail());
        String extra = normalizedOptional(entityId);
        String message = normalizedOptional(details);
        if (!extra.isBlank()) {
            message = message.isBlank() ? extra : message + " [" + extra + "]";
        }
        AuditService.log(performedBy, action, entity, message);
    }

    public static List<String> getDepartments() {
        List<String> departments = new ArrayList<>();

        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM departments ORDER BY name")) {

            while (rs.next()) {
                departments.add(rs.getString("name"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return departments;
    }

    public static List<String> getEquipmentCategories() {
        List<String> categories = new ArrayList<>();

        String sql =
                "SELECT DISTINCT TRIM(category) AS category " +
                "FROM equipment " +
                "WHERE category IS NOT NULL AND TRIM(category) <> '' " +
                "UNION " +
                "SELECT DISTINCT TRIM(equipment_type) AS category " +
                "FROM assignments " +
                "WHERE equipment_type IS NOT NULL AND TRIM(equipment_type) <> '' " +
                "ORDER BY category";

        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                categories.add(rs.getString("category"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return categories;
    }

    public static void updateDepartment(String oldName, String newName) throws Exception {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                insertDepartmentIfMissing(conn, newName);

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET department=? WHERE TRIM(department)=TRIM(?)"
                )) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE assignments SET department=? WHERE TRIM(department)=TRIM(?)"
                )) {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM departments WHERE TRIM(name)=TRIM(?)"
                )) {
                    ps.setString(1, oldName);
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public static void deleteDepartment(String department) throws Exception {
        try (Connection conn = getConnection()) {
            if (isDepartmentInUse(conn, department)) {
                throw new Exception("Department is still in use by users or assignments.");
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM departments WHERE TRIM(name)=TRIM(?)"
            )) {
                ps.setString(1, department);
                ps.executeUpdate();
            }
        }
    }

    private static boolean isDepartmentInUse(Connection conn, String department) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT " +
                        "(SELECT COUNT(*) FROM users WHERE TRIM(department)=TRIM(?)) + " +
                        "(SELECT COUNT(*) FROM assignments WHERE TRIM(department)=TRIM(?))"
        )) {
            ps.setString(1, department);
            ps.setString(2, department);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private static void insertDepartmentIfMissing(Connection conn, String department) throws SQLException {
        if (department == null || department.isBlank()) {
            return;
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO departments(name) VALUES (?)"
        )) {
            ps.setString(1, department.trim());
            ps.executeUpdate();
        }
    }

    // ================= STOCK =================
    public static int getAvailableStock(String type) {
        try (Connection conn = getConnection()) {
            return getAvailableStock(conn, type);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static Map<String, Integer> getAvailableStockByCategory() {
        Map<String, Integer> stockByCategory = new LinkedHashMap<>();

        String sql =
            "SELECT TRIM(category) AS category, COUNT(*) AS total " +
            "FROM equipment e " +
            "WHERE UPPER(COALESCE(e.status, 'AVAILABLE')) = 'AVAILABLE' " +
            "AND e.asset_code NOT IN (" +
            "SELECT asset_code FROM distribution WHERE returned = 0" +
            ") " +
            "GROUP BY TRIM(category) " +
            "ORDER BY TRIM(category)";

        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                String category = rs.getString("category");
                if (category != null && !category.isBlank()) {
                    stockByCategory.put(category, rs.getInt("total"));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return stockByCategory;
    }

    // ================= DISTRIBUTION =================
    public static void distributeEquipment(String assetCode,
                                           int assignmentId,
                                           String name,
                                           String phone,
                                           String nid) throws Exception {

        String checkSql = "SELECT COUNT(*) FROM distribution WHERE asset_code = ? AND returned = 0";
        String insertSql = "INSERT INTO distribution (asset_code, assignment_id, assigned_to, phone, nid, date, returned) " +
                           "VALUES (?, ?, ?, ?, ?, DATE('now'), 0)";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                    check.setString(1, assetCode);
                    ResultSet rs = check.executeQuery();
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new Exception("Equipment already assigned");
                    }
                }

                validateDistribution(conn, assignmentId, assetCode, name, phone, nid);

                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, assetCode);
                    ps.setInt(2, assignmentId);
                    ps.setString(3, name);
                    ps.setString(4, phone);
                    ps.setString(5, nid);
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public static void distributeEquipmentBatch(int assignmentId, List<Distribution> distributions) throws Exception {
        String checkSql = "SELECT COUNT(*) FROM distribution WHERE asset_code = ? AND returned = 0";
        String insertSql = "INSERT INTO distribution (asset_code, assignment_id, assigned_to, phone, nid, date, returned) " +
                "VALUES (?, ?, ?, ?, ?, DATE('now'), 0)";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            List<String> distributedAssetCodes = new ArrayList<>();
            try (PreparedStatement check = conn.prepareStatement(checkSql);
                 PreparedStatement insert = conn.prepareStatement(insertSql)) {

                for (Distribution distribution : distributions) {
                    check.setString(1, distribution.getAssetCode());
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            throw new Exception("Equipment already assigned: " + distribution.getAssetCode());
                        }
                    }

                    validateDistribution(
                            conn,
                            assignmentId,
                            distribution.getAssetCode(),
                            distribution.getAssignedTo(),
                            distribution.getPhone(),
                            distribution.getNid()
                    );

                    insert.setString(1, distribution.getAssetCode());
                    insert.setInt(2, assignmentId);
                    insert.setString(3, distribution.getAssignedTo());
                    insert.setString(4, distribution.getPhone());
                    insert.setString(5, distribution.getNid());
                    insert.executeUpdate();
                    distributedAssetCodes.add(distribution.getAssetCode());
                }

                conn.commit();
                logAudit(
                        "DISTRIBUTE_EQUIPMENT_BATCH",
                        "DISTRIBUTION",
                        Integer.toString(assignmentId),
                        "Distributed " + distributedAssetCodes.size() + " equipment item(s) under assignment " +
                                assignmentId + ": " + String.join(", ", distributedAssetCodes)
                );
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public static List<String> getAvailableEquipment() {

        List<String> list = new ArrayList<>();

        String sql =
            "SELECT asset_code FROM equipment " +
            "WHERE UPPER(COALESCE(status, 'AVAILABLE')) = 'AVAILABLE' " +
            "AND asset_code NOT IN (SELECT asset_code FROM distribution WHERE returned = 0)";

        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                list.add(rs.getString("asset_code"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    public static List<String> getAvailableEquipmentByCategory(String category) {
        List<String> list = new ArrayList<>();

        String sql =
            "SELECT asset_code FROM equipment " +
            "WHERE TRIM(category) = TRIM(?) " +
            "AND UPPER(COALESCE(status, 'AVAILABLE')) = 'AVAILABLE' " +
            "AND asset_code NOT IN (SELECT asset_code FROM distribution WHERE returned = 0) " +
            "ORDER BY asset_code";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, category);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString("asset_code"));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    public static List<Distribution> getCurrentDistributions() {
        List<Distribution> list = new ArrayList<>();

        String sql =
            "SELECT id, asset_code, assigned_to, phone, nid, date " +
            "FROM distribution " +
            "WHERE returned = 0 " +
            "ORDER BY date DESC, id DESC";

        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                Distribution distribution = new Distribution(
                        rs.getInt("id"),
                        rs.getString("asset_code"),
                        "",
                        rs.getString("assigned_to"),
                        rs.getString("phone"),
                        rs.getString("nid"),
                        java.time.LocalDate.parse(rs.getString("date"))
                );
                list.add(distribution);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    public static Distribution getCurrentDistributionForAssignment(int assignmentId) {
        String sql =
                "SELECT id, asset_code, assigned_to, phone, nid, date " +
                "FROM distribution " +
                "WHERE assignment_id = ? AND returned = 0 " +
                "ORDER BY date DESC, id DESC " +
                "LIMIT 1";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, assignmentId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Distribution(
                            rs.getInt("id"),
                            rs.getString("asset_code"),
                            "",
                            rs.getString("assigned_to"),
                            rs.getString("phone"),
                            rs.getString("nid"),
                            java.time.LocalDate.parse(rs.getString("date"))
                    );
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static Distribution getCurrentDistributionForAsset(String assetCode) {
        String sql =
                "SELECT id, asset_code, assigned_to, phone, nid, date " +
                "FROM distribution " +
                "WHERE asset_code = ? AND returned = 0 " +
                "ORDER BY date DESC, id DESC " +
                "LIMIT 1";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, assetCode);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Distribution(
                            rs.getInt("id"),
                            rs.getString("asset_code"),
                            "",
                            rs.getString("assigned_to"),
                            rs.getString("phone"),
                            rs.getString("nid"),
                            java.time.LocalDate.parse(rs.getString("date"))
                    );
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static List<String> getOutstandingAssetCodesForAssignment(int assignmentId) {
        List<String> assetCodes = new ArrayList<>();

        String sql =
                "SELECT asset_code " +
                "FROM distribution " +
                "WHERE assignment_id = ? AND returned = 0 " +
                "ORDER BY date DESC, id DESC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, assignmentId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    assetCodes.add(rs.getString("asset_code"));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return assetCodes;
    }

    // ================= RETURN EQUIPMENT =================
    public static void returnEquipment(String assetCode, String returnedBy,
                                       String phone, String nid,
                                       String condition, String remarks) throws Exception {

        String updateSql = "UPDATE distribution SET returned = 1 WHERE asset_code = ? AND returned = 0";
        String statusSql = "UPDATE equipment SET status = 'AVAILABLE', condition = ? WHERE asset_code = ?";
        String insertSql = "INSERT INTO returns (asset_code, returned_by, phone, nid, condition, remarks, return_date) " +
                           "VALUES (?, ?, ?, ?, ?, ?, DATE('now'))";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps1 = conn.prepareStatement(updateSql)) {
                    ps1.setString(1, assetCode);
                    int updated = ps1.executeUpdate();
                    if (updated == 0) {
                        throw new Exception("Asset not found or already returned: " + assetCode);
                    }
                }

                try (PreparedStatement ps2 = conn.prepareStatement(insertSql)) {
                    ps2.setString(1, assetCode);
                    ps2.setString(2, returnedBy);
                    ps2.setString(3, phone);
                    ps2.setString(4, nid);
                    ps2.setString(5, condition);
                    ps2.setString(6, remarks);
                    ps2.executeUpdate();
                }

                try (PreparedStatement ps3 = conn.prepareStatement(statusSql)) {
                    ps3.setString(1, normalizedOptional(condition));
                    ps3.setString(2, assetCode);
                    ps3.executeUpdate();
                }

                conn.commit();
                logAudit(
                        "RETURN_EQUIPMENT",
                        "RETURNS",
                        assetCode,
                        "Equipment returned by " + returnedBy + ". Condition: " + condition +
                                ", phone: " + phone + ", NID: " + nid +
                                (normalizedOptional(remarks).isBlank() ? "" : ", remarks: " + remarks)
                );
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // ================= DISTRIBUTED ASSET LOOKUP =================
    public static boolean isAssetCurrentlyDistributed(String assetCode) {
        String sql = "SELECT COUNT(*) FROM distribution WHERE asset_code = ? AND returned = 0";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assetCode);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static int getDistributedCountForAssignment(int assignmentId) {
        try (Connection conn = getConnection()) {
            return getDistributedCountForAssignment(conn, assignmentId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static int getDistributedCountForAssignment(Connection conn, int assignmentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM distribution WHERE assignment_id = ? AND returned = 0";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, assignmentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    private static int getAvailableStock(Connection conn, String type) throws SQLException {
        String sql =
                "SELECT COUNT(*) FROM equipment e " +
                        "WHERE TRIM(e.category) = TRIM(?) " +
                        "AND UPPER(COALESCE(e.status, 'AVAILABLE')) = 'AVAILABLE' " +
                        "AND e.asset_code NOT IN (" +
                        "SELECT asset_code FROM distribution WHERE returned = 0" +
                        ")";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizedOptional(type));
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public static void updateOutstandingReturnRemarks(List<String> assetCodes, String remarks) throws Exception {
        if (assetCodes == null || assetCodes.isEmpty()) {
            return;
        }

        String sql = "UPDATE distribution SET outstanding_remarks = ? WHERE asset_code = ? AND returned = 0";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String assetCode : assetCodes) {
                ps.setString(1, normalizedOptional(remarks));
                ps.setString(2, normalizedOptional(assetCode));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public static void updateOutstandingReturnRemarks(Map<String, String> assetRemarks) throws Exception {
        if (assetRemarks == null || assetRemarks.isEmpty()) {
            return;
        }

        String sql = "UPDATE distribution SET outstanding_remarks = ? WHERE asset_code = ? AND returned = 0";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, String> entry : assetRemarks.entrySet()) {
                ps.setString(1, normalizedOptional(entry.getValue()));
                ps.setString(2, normalizedOptional(entry.getKey()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void validateDistribution(Connection conn, int assignmentId, String assetCode, String name, String phone, String nid)
            throws Exception {
        String normalizedAssetCode = normalizedRequired(assetCode, "Asset code is required.");
        String normalizedName = normalizedRequired(name, "Assigned person is required.");
        String normalizedPhone = normalizedRequired(phone, "Phone is required.");
        String normalizedNid = normalizedRequired(nid, "NID is required.");

        String assignmentSql = "SELECT person, equipment_type, COALESCE(status, 'ACTIVE') AS assignment_status FROM assignments WHERE id = ?";
        String equipmentSql = "SELECT category, status FROM equipment WHERE asset_code = ?";

        String assignmentType;
        try (PreparedStatement ps = conn.prepareStatement(assignmentSql)) {
            ps.setInt(1, assignmentId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new Exception("Assignment record was not found.");
            }
            assignmentType = normalizeEquipmentCategory(rs.getString("equipment_type"));
            String assignmentStatus = normalizedOptional(rs.getString("assignment_status"));
            if (AccessControl.STATUS_FROZEN.equalsIgnoreCase(assignmentStatus)) {
                throw new Exception("Frozen assignments cannot be changed.");
            }
            if (AccessControl.STATUS_RETIRED.equalsIgnoreCase(assignmentStatus)) {
                throw new Exception("Retired assignments are closed.");
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(equipmentSql)) {
            ps.setString(1, normalizedAssetCode);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new Exception("Selected equipment does not exist: " + normalizedAssetCode);
            }

            String equipmentCategory = normalizeEquipmentCategory(rs.getString("category"));
            String equipmentStatus = normalizedOptional(rs.getString("status"));
            if (!assignmentType.equalsIgnoreCase(equipmentCategory)) {
                throw new Exception("Equipment " + normalizedAssetCode + " is " + equipmentCategory +
                        " but the assignment requires " + assignmentType + ".");
            }
            if (!"AVAILABLE".equalsIgnoreCase(equipmentStatus)) {
                if (AccessControl.STATUS_RETIRED.equalsIgnoreCase(equipmentStatus)) {
                    throw new Exception("Retired equipment cannot be assigned: " + normalizedAssetCode);
                }
                throw new Exception("Equipment is already assigned: " + normalizedAssetCode);
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE equipment SET status = 'ASSIGNED' WHERE asset_code = ?"
        )) {
            ps.setString(1, normalizedAssetCode);
            ps.executeUpdate();
        }
    }

    private static boolean hasDistributionHistory(Connection conn, String assetCode) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM distribution WHERE asset_code = ?"
        )) {
            ps.setString(1, assetCode);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private static String findAssetCodeBySerial(Connection conn, String serialNumber) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT asset_code FROM equipment WHERE LOWER(TRIM(serial_number)) = LOWER(TRIM(?)) ORDER BY id DESC LIMIT 1"
        )) {
            ps.setString(1, normalizedOptional(serialNumber));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? normalizedOptional(rs.getString("asset_code")) : "";
            }
        }
    }

    private static String findEquipmentSnapshot(Connection conn, String assetCode) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT asset_code, name, category, serial_number, condition, source, status FROM equipment WHERE asset_code = ?"
        )) {
            ps.setString(1, normalizedOptional(assetCode));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return "not found";
                }
                return "asset=" + normalizedOptional(rs.getString("asset_code")) +
                        ", name=" + normalizedOptional(rs.getString("name")) +
                        ", category=" + normalizedOptional(rs.getString("category")) +
                        ", serial=" + normalizedOptional(rs.getString("serial_number")) +
                        ", condition=" + normalizedOptional(rs.getString("condition")) +
                        ", source=" + normalizedOptional(rs.getString("source")) +
                        ", status=" + normalizedOptional(rs.getString("status"));
            }
        }
    }

    private static String findAssignmentSnapshot(Connection conn, int assignmentId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, person, department, equipment_type, reason, quantity, status, date FROM assignments WHERE id = ?"
        )) {
            ps.setInt(1, assignmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return "not found";
                }
                return "id=" + rs.getInt("id") +
                        ", person=" + normalizedOptional(rs.getString("person")) +
                        ", department=" + normalizedOptional(rs.getString("department")) +
                        ", equipment=" + normalizedOptional(rs.getString("equipment_type")) +
                        ", quantity=" + rs.getInt("quantity") +
                        ", status=" + normalizedOptional(rs.getString("status")) +
                        ", date=" + normalizedOptional(rs.getString("date")) +
                        ", reason=" + normalizedOptional(rs.getString("reason"));
            }
        }
    }

    private static String resolveAuditUsername(ResultSet rs) throws SQLException {
        String email = normalizedOptional(rs.getString("email"));
        if (!email.isBlank()) {
            return email;
        }
        String username = normalizedOptional(rs.getString("username"));
        return username.isBlank() ? "unknown_user" : username;
    }

    private static boolean hasAssignmentDistributionHistory(Connection conn, int assignmentId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM distribution WHERE assignment_id = ?"
        )) {
            ps.setInt(1, assignmentId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    public static String getAssignmentLifecycleStatus(int assignmentId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COALESCE(status, 'ACTIVE') FROM assignments WHERE id = ?"
             )) {
            ps.setInt(1, assignmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? normalizedOptional(rs.getString(1)) : AccessControl.STATUS_ACTIVE;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return AccessControl.STATUS_ACTIVE;
        }
    }

    public static void updateAssignmentStatus(int id, String status) throws Exception {
        String normalizedStatus = normalizedRequired(status, "Status is required.").toUpperCase();
        if (!AccessControl.STATUS_ACTIVE.equalsIgnoreCase(normalizedStatus)
                && !AccessControl.STATUS_FROZEN.equalsIgnoreCase(normalizedStatus)
                && !AccessControl.STATUS_RETIRED.equalsIgnoreCase(normalizedStatus)) {
            throw new Exception("Invalid assignment status.");
        }
        AccessControl.requireRole(AccessControl.ROLE_SUPER_ADMIN, AccessControl.ROLE_ADMIN);

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE assignments SET status=? WHERE id=?"
             )) {
            ps.setString(1, normalizedStatus);
            ps.setInt(2, id);
            if (ps.executeUpdate() == 0) {
                throw new Exception("Assignment record was not found.");
            }
            logAudit(
                    "ASSIGNMENT_" + normalizedStatus,
                    "ASSIGNMENTS",
                    Integer.toString(id),
                    "Assignment status changed to " + normalizedStatus
            );
        }
    }

    public static void updateEquipmentStatus(String assetCode, String status) throws Exception {
        String normalizedAssetCode = normalizedRequired(assetCode, "Asset code is required.");
        String normalizedStatus = normalizedRequired(status, "Status is required.").toUpperCase();
        if (!AccessControl.STATUS_RETIRED.equalsIgnoreCase(normalizedStatus)
                && !AccessControl.STATUS_ACTIVE.equalsIgnoreCase(normalizedStatus)
                && !"AVAILABLE".equalsIgnoreCase(normalizedStatus)) {
            throw new Exception("Invalid equipment status.");
        }
        String persistedStatus = AccessControl.STATUS_ACTIVE.equalsIgnoreCase(normalizedStatus) ? "AVAILABLE" : normalizedStatus;

        AccessControl.requireRole(AccessControl.ROLE_SUPER_ADMIN, AccessControl.ROLE_ADMIN);

        try (Connection conn = getConnection()) {
            if (hasOpenDistribution(conn, normalizedAssetCode)) {
                throw new Exception("Assigned equipment cannot be retired or restored.");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE equipment SET status=? WHERE asset_code=?"
            )) {
                ps.setString(1, persistedStatus);
                ps.setString(2, normalizedAssetCode);
                if (ps.executeUpdate() == 0) {
                    throw new Exception("Equipment record was not found.");
                }
            }
            logAudit(
                    "EQUIPMENT_" + persistedStatus,
                    "EQUIPMENT",
                    normalizedAssetCode,
                    "Equipment status changed to " + persistedStatus
            );
        }
    }

    private static boolean hasOpenDistribution(Connection conn, String assetCode) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM distribution WHERE asset_code = ? AND returned = 0"
        )) {
            ps.setString(1, assetCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static void requireAssignmentMutable(Connection conn, int assignmentId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(status, 'ACTIVE') FROM assignments WHERE id = ?"
        )) {
            ps.setInt(1, assignmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new Exception("Assignment record was not found.");
                }
                String status = normalizedOptional(rs.getString(1));
                if (AccessControl.STATUS_FROZEN.equalsIgnoreCase(status)) {
                    throw new Exception("Frozen assignments cannot be changed.");
                }
                if (AccessControl.STATUS_RETIRED.equalsIgnoreCase(status)) {
                    throw new Exception("Retired assignments are closed.");
                }
            }
        }
    }

    private static String normalizedRequired(String value, String message) throws Exception {
        String normalized = normalizedOptional(value);
        if (normalized.isBlank()) {
            throw new Exception(message);
        }
        return normalized;
    }

    private static String normalizedOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeEquipmentCategory(String category) throws Exception {
        return normalizedRequired(category, "Equipment type is required.");
    }

    private static String buildAssetCode(String category, int id) {
        String normalizedCategory = normalizedOptional(category).toUpperCase().replace(" ", "");
        if (normalizedCategory.length() < 3) {
            normalizedCategory = (normalizedCategory + "OTH").substring(0, 3);
        } else {
            normalizedCategory = normalizedCategory.substring(0, 3);
        }
        return "MSR-" + normalizedCategory + "-" + String.format("%03d", id);
    }

    private static Path resolveDatabasePath() {
        return FileLocationHelper.resolveCanonicalDataFile(DATABASE_FILE_NAME);
    }

    private static final class UserResetTarget {
        private final int id;
        private final String username;
        private final String email;
        private final String resetCode;
        private final LocalDateTime resetExpiry;
        private final LocalDateTime resetRequestedAt;

        private UserResetTarget(int id, String username, String email, String resetCode,
                                LocalDateTime resetExpiry, LocalDateTime resetRequestedAt) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.resetCode = resetCode;
            this.resetExpiry = resetExpiry;
            this.resetRequestedAt = resetRequestedAt;
        }
    }
}
