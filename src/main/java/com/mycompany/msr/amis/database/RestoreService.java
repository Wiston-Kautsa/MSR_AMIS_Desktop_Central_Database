package com.mycompany.msr.amis;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RestoreService {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DISPLAY_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final String SQLITE_URL_PREFIX = "jdbc:sqlite:";

    private final BackupSyncConfig config;

    public RestoreService(BackupSyncConfig config) {
        this.config = config;
    }

    public BackupSyncConfig getConfig() {
        return config;
    }

    public Path findLatestOfficialDatabase() throws IOException {
        return listDatabaseFiles(config.getOfficialPath()).stream()
                .max(Comparator.comparing(this::lastModifiedSafe))
                .orElse(null);
    }

    public RestoreResult restoreLatestOfficialDatabase(String actor) throws Exception {
        Path latestOfficial = findLatestOfficialDatabase();
        if (latestOfficial == null) {
            throw new IOException("No official database file was found.");
        }
        if (!Files.exists(config.getLocalDatabasePath())) {
            throw new IOException("Local database file was not found: " + config.getLocalDatabasePath());
        }

        Path safetyBackup = backupCurrentLocalDatabaseBeforeRestore(sanitizedActor(actor), "pre_restore");
        AuditService.log(
                actor,
                "CREATE_BACKUP",
                "BACKUP_SYNC",
                "Created safety backup before restore: " + safetyBackup.getFileName()
        );
        Files.copy(latestOfficial, config.getLocalDatabasePath(), StandardCopyOption.REPLACE_EXISTING);
        AuditService.log(
                actor,
                "RESTORE_DATABASE",
                "BACKUP_SYNC",
                "Restored local database from official file: " + latestOfficial.getFileName()
        );
        return new RestoreResult(latestOfficial, safetyBackup);
    }

    public Path submitBackup(String actor) throws Exception {
        Path localBackup = backupCurrentLocalDatabaseBeforeRestore(sanitizedActor(actor), "submission");
        Path submissionCopy = config.getSubmissionsPath().resolve(localBackup.getFileName());
        Files.copy(localBackup, submissionCopy, StandardCopyOption.REPLACE_EXISTING);
        AuditService.log(
                actor,
                "CREATE_BACKUP",
                "BACKUP_SYNC",
                "Created backup file: " + localBackup.getFileName()
        );
        AuditService.log(
                actor,
                "SUBMIT_BACKUP",
                "BACKUP_SYNC",
                "Submitted backup file to SUBMISSIONS: " + submissionCopy.getFileName()
        );
        return submissionCopy;
    }

    public DemoResetResult resetLocalDemoData(String actor) throws Exception {
        if (!Files.exists(config.getLocalDatabasePath())) {
            throw new IOException("Local database file was not found: " + config.getLocalDatabasePath());
        }

        String sanitizedActor = sanitizedActor(actor);
        Path safetyBackup = backupCurrentLocalDatabaseBeforeRestore(sanitizedActor, "pre_demo_reset");
        Map<String, Integer> clearedRows = new LinkedHashMap<>();

        try (Connection connection = openConnection(config.getLocalDatabasePath())) {
            connection.setAutoCommit(false);
            try {
                clearedRows.put("returns", deleteAll(connection, "returns"));
                clearedRows.put("distribution", deleteAll(connection, "distribution"));
                clearedRows.put("assignments", deleteAll(connection, "assignments"));
                clearedRows.put("equipment", deleteAll(connection, "equipment"));
                clearedRows.put("password_reset_audit", deleteAll(connection, "password_reset_audit"));
                clearedRows.put("audit_log", deleteAll(connection, "audit_log"));
                resetAutoincrementCounters(connection,
                        "returns",
                        "distribution",
                        "assignments",
                        "equipment",
                        "password_reset_audit",
                        "audit_log");
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }

        StringBuilder summary = new StringBuilder("Operational demo data cleared.");
        for (Map.Entry<String, Integer> entry : clearedRows.entrySet()) {
            summary.append(" ")
                    .append(entry.getKey())
                    .append("=")
                    .append(entry.getValue())
                    .append(";");
        }
        summary.append(" Safety backup: ").append(safetyBackup.getFileName()).append(".");

        AuditService.log(
                actor,
                "RESET_DEMO_DATA",
                "BACKUP_SYNC",
                summary.toString()
        );

        return new DemoResetResult(safetyBackup, clearedRows);
    }

    public PublishResult approveAndPublish(Path submissionFile, String actor) throws Exception {
        if (submissionFile == null || !Files.exists(submissionFile)) {
            throw new IOException("Select a valid submitted backup file.");
        }

        validateSubmissionDatabase(submissionFile);

        String publishedName = "master_msr_amis_" + FILE_STAMP.format(Instant.now()) + ".db";
        Path publishedFile = config.getOfficialPath().resolve(publishedName);
        Path latestOfficial = findLatestOfficialDatabase();
        PublishResult result;

        if (latestOfficial == null) {
            Files.copy(submissionFile, publishedFile, StandardCopyOption.REPLACE_EXISTING);
            result = new PublishResult(
                    publishedFile,
                    "Validated submission schema successfully.",
                    "No existing official database was found. The approved backup became the first official version."
            );
        } else {
            Files.copy(latestOfficial, publishedFile, StandardCopyOption.REPLACE_EXISTING);
            try {
                result = mergeSubmissionIntoPublishedDatabase(submissionFile, publishedFile, actor);
            } catch (Exception e) {
                Files.deleteIfExists(publishedFile);
                throw e;
            }
        }

        AuditService.log(
                actor,
                "APPROVE_SUBMISSION",
                "BACKUP_SYNC",
                "Approved submission file: " + submissionFile.getFileName()
        );
        AuditService.log(
                actor,
                "PUBLISH_OFFICIAL",
                "BACKUP_SYNC",
                "Published official database: " + publishedFile.getFileName() +
                        " | " + result.getMergeSummary()
        );
        Files.deleteIfExists(submissionFile);
        AuditService.log(
                actor,
                "CLEAR_SUBMISSION",
                "BACKUP_SYNC",
                "Cleared approved submission from queue: " + submissionFile.getFileName()
        );
        return result;
    }

    public List<BackupSyncRecord> listMyBackups(String actor) throws IOException {
        String sanitizedActor = sanitizedActor(actor);
        List<BackupSyncRecord> records = new ArrayList<>();
        for (Path file : listDatabaseFiles(config.getLocalBackupPath())) {
            if (file.getFileName().toString().contains("_" + sanitizedActor + "_")) {
                records.add(toRecord(file, actor, "LOCAL", config.getLocalBackupPath()));
            }
        }
        records.sort(Comparator.comparing(BackupSyncRecord::getTimestamp).reversed());
        return records;
    }

    public List<BackupSyncRecord> listSubmissions() throws IOException {
        List<BackupSyncRecord> records = new ArrayList<>();
        for (Path file : listDatabaseFiles(config.getSubmissionsPath())) {
            records.add(toRecord(file, inferActor(file), "PENDING", config.getSubmissionsPath()));
        }
        records.sort(Comparator.comparing(BackupSyncRecord::getTimestamp).reversed());
        return records;
    }

    public List<BackupSyncRecord> listOfficialHistory() throws IOException {
        List<BackupSyncRecord> records = new ArrayList<>();
        for (Path file : listDatabaseFiles(config.getOfficialPath())) {
            records.add(toRecord(file, "System", "OFFICIAL", config.getOfficialPath()));
        }
        records.sort(Comparator.comparing(BackupSyncRecord::getTimestamp).reversed());
        return records;
    }

    public String describeLatestOfficialVersion() throws IOException {
        Path latest = findLatestOfficialDatabase();
        if (latest == null) {
            return "Not Available";
        }
        return latest.getFileName() + " | " + formatTimestamp(lastModifiedSafe(latest));
    }

    public String describeLocalVersion() throws IOException {
        Path localDb = config.getLocalDatabasePath();
        if (!Files.exists(localDb)) {
            return "Missing local database";
        }
        return localDb.getFileName() + " | " + formatTimestamp(lastModifiedSafe(localDb));
    }

    public CompatibilityReport analyzeSubmission(Path submissionFile) throws Exception {
        if (submissionFile == null || !Files.exists(submissionFile)) {
            throw new IOException("Select a valid submitted backup file.");
        }

        validateSubmissionDatabase(submissionFile);

        Path latestOfficial = findLatestOfficialDatabase();
        List<String> conflicts = new ArrayList<>();
        MergeStats estimates = new MergeStats();
        int submissionUsers;
        int submissionEquipment;
        int submissionAssignments;
        int submissionDistributions;
        int submissionReturns;

        try (Connection source = openConnection(submissionFile)) {
            submissionUsers = countRows(source, "users");
            submissionEquipment = countRows(source, "equipment");
            submissionAssignments = countRows(source, "assignments");
            submissionDistributions = countRows(source, "distribution");
            submissionReturns = countRows(source, "returns");

            if (latestOfficial != null) {
                try (Connection target = openConnection(latestOfficial)) {
                    analyzeDepartments(source, target, estimates);
                    analyzeUsers(source, target, estimates, conflicts);
                    analyzeEquipment(source, target, estimates, conflicts);
                    analyzeAssignments(source, target, estimates);
                    analyzeDistribution(source, target, estimates, conflicts);
                    analyzeReturns(source, target, estimates, conflicts);
                }
            } else {
                estimates.usersMerged = submissionUsers;
                estimates.equipmentMerged = submissionEquipment;
                estimates.assignmentsMerged = submissionAssignments;
                estimates.distributionMerged = submissionDistributions;
                estimates.returnsMerged = submissionReturns;
            }
        }

        String officialVersion = latestOfficial == null
                ? "No official database yet"
                : latestOfficial.getFileName() + " | " + formatTimestamp(lastModifiedSafe(latestOfficial));

        return new CompatibilityReport(
                submissionFile,
                officialVersion,
                submissionUsers,
                submissionEquipment,
                submissionAssignments,
                submissionDistributions,
                submissionReturns,
                conflicts.isEmpty(),
                conflicts,
                estimates.toSummary()
        );
    }

    private Path backupCurrentLocalDatabaseBeforeRestore(String actor, String prefix) throws IOException {
        if (!Files.exists(config.getLocalDatabasePath())) {
            throw new IOException("Local database file was not found: " + config.getLocalDatabasePath());
        }
        Files.createDirectories(config.getLocalBackupPath());

        String fileName = "msr_amis_" + prefix + "_" + actor + "_" + FILE_STAMP.format(Instant.now()) + ".db";
        Path backupFile = config.getLocalBackupPath().resolve(fileName);
        Files.copy(config.getLocalDatabasePath(), backupFile, StandardCopyOption.REPLACE_EXISTING);
        return backupFile;
    }

    private int deleteAll(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate("DELETE FROM " + tableName);
        }
    }

    private void resetAutoincrementCounters(Connection connection, String... tableNames) throws SQLException {
        if (!tableExists(connection, "sqlite_sequence")) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM sqlite_sequence WHERE name = ?"
        )) {
            for (String tableName : tableNames) {
                ps.setString(1, tableName);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private List<Path> listDatabaseFiles(Path directory) throws IOException {
        Files.createDirectories(directory);
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.db")) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    files.add(path);
                }
            }
        }
        return files;
    }

    private BackupSyncRecord toRecord(Path file, String actor, String status, Path basePath) throws IOException {
        return new BackupSyncRecord(
                file.getFileName().toString(),
                actor,
                formatTimestamp(lastModifiedSafe(file)),
                basePath.toString(),
                status,
                file
        );
    }

    private String inferActor(Path file) {
        String name = file.getFileName().toString();
        String[] parts = name.split("_");
        if (parts.length >= 4) {
            return parts[3];
        }
        return "-";
    }

    private FileTime lastModifiedSafe(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    private String formatTimestamp(FileTime fileTime) {
        return DISPLAY_STAMP.format(fileTime.toInstant());
    }

    private String sanitizedActor(String actor) {
        String raw = actor == null || actor.isBlank() ? "user" : actor.trim().toLowerCase();
        return raw.replaceAll("[^a-z0-9._-]", "_");
    }

    private int countRows(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void analyzeDepartments(Connection source, Connection target, MergeStats estimates) throws SQLException {
        if (!tableExists(source, "departments")) {
            return;
        }

        try (PreparedStatement ps = source.prepareStatement("SELECT name FROM departments");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (name != null && !name.isBlank() && !departmentExists(target, name)) {
                    estimates.departmentsMerged++;
                }
            }
        }
    }

    private void analyzeUsers(Connection source, Connection target, MergeStats estimates, List<String> conflicts)
            throws Exception {
        if (!tableExists(source, "users")) {
            return;
        }

        String sql = "SELECT username, email, phone FROM users";
        try (PreparedStatement ps = source.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String username = rs.getString("username");
                String email = rs.getString("email");
                String phone = rs.getString("phone");

                if (userExists(target, username, email)) {
                    estimates.usersSkipped++;
                    continue;
                }

                if (phoneConflictExists(target, phone)) {
                    conflicts.add("User phone conflict: " + phone + " already exists in official database.");
                    continue;
                }

                estimates.usersMerged++;
            }
        }
    }

    private void analyzeEquipment(Connection source, Connection target, MergeStats estimates, List<String> conflicts)
            throws Exception {
        String sql = "SELECT asset_code, serial_number, name, category FROM equipment";
        try (PreparedStatement ps = source.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String assetCode = rs.getString("asset_code");
                String serialNumber = rs.getString("serial_number");
                String name = rs.getString("name");
                String category = rs.getString("category");

                EquipmentSnapshot existingByAsset = findEquipmentByAssetCode(target, assetCode);
                if (existingByAsset != null) {
                    if (!sameEquipmentIdentity(existingByAsset, serialNumber, name, category)) {
                        conflicts.add("Equipment asset conflict: " + assetCode + " has different identity in official database.");
                    } else {
                        estimates.equipmentSkipped++;
                    }
                    continue;
                }

                EquipmentSnapshot existingBySerial = findEquipmentBySerialNumber(target, serialNumber);
                if (existingBySerial != null && !normalize(existingBySerial.assetCode).equals(normalize(assetCode))) {
                    conflicts.add("Equipment serial conflict: " + serialNumber + " already belongs to " + existingBySerial.assetCode + ".");
                    continue;
                }

                estimates.equipmentMerged++;
            }
        }
    }

    private void analyzeAssignments(Connection source, Connection target, MergeStats estimates) throws SQLException {
        String sql = "SELECT person, department, equipment_type, reason, quantity, date FROM assignments";
        try (PreparedStatement ps = source.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Integer existingId = findAssignmentId(
                        target,
                        rs.getString("person"),
                        rs.getString("department"),
                        rs.getString("equipment_type"),
                        rs.getString("reason"),
                        rs.getInt("quantity"),
                        rs.getString("date")
                );
                if (existingId != null) {
                    estimates.assignmentsSkipped++;
                } else {
                    estimates.assignmentsMerged++;
                }
            }
        }
    }

    private void analyzeDistribution(Connection source, Connection target, MergeStats estimates, List<String> conflicts)
            throws Exception {
        if (!tableExists(source, "distribution")) {
            return;
        }

        Map<Integer, AssignmentFingerprint> assignmentMap = loadAssignmentFingerprints(source);
        String sql = "SELECT asset_code, assignment_id, assigned_to, phone, nid, date, returned FROM distribution";
        try (PreparedStatement ps = source.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String assetCode = rs.getString("asset_code");
                AssignmentFingerprint fingerprint = assignmentMap.get(rs.getInt("assignment_id"));
                Integer targetAssignmentId = fingerprint == null ? null : findAssignmentId(
                        target,
                        fingerprint.person,
                        fingerprint.department,
                        fingerprint.equipmentType,
                        fingerprint.reason,
                        fingerprint.quantity,
                        fingerprint.date
                );

                if (targetAssignmentId == null) {
                    conflicts.add("Distribution assignment conflict: " + assetCode + " references an assignment not found in official database.");
                    continue;
                }

                Integer existingId = findDistributionId(
                        target,
                        assetCode,
                        targetAssignmentId,
                        rs.getString("assigned_to"),
                        rs.getString("phone"),
                        rs.getString("nid"),
                        rs.getString("date")
                );
                if (existingId != null) {
                    estimates.distributionSkipped++;
                    if (rs.getInt("returned") == 1) {
                        estimates.distributionUpdated++;
                    }
                    continue;
                }

                if (rs.getInt("returned") == 0 && hasOpenDistributionConflict(target, assetCode)) {
                    conflicts.add("Open distribution conflict: asset " + assetCode + " is already assigned in official database.");
                    continue;
                }

                estimates.distributionMerged++;
            }
        }
    }

    private void analyzeReturns(Connection source, Connection target, MergeStats estimates, List<String> conflicts)
            throws Exception {
        if (!tableExists(source, "returns")) {
            return;
        }

        String sql = "SELECT asset_code, returned_by, phone, nid, condition, remarks, return_date FROM returns";
        try (PreparedStatement ps = source.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String assetCode = rs.getString("asset_code");
                if (findEquipmentByAssetCode(target, assetCode) == null) {
                    conflicts.add("Return conflict: asset " + assetCode + " does not exist in official database.");
                    continue;
                }

                if (returnExists(
                        target,
                        assetCode,
                        rs.getString("returned_by"),
                        rs.getString("phone"),
                        rs.getString("nid"),
                        rs.getString("condition"),
                        rs.getString("remarks"),
                        rs.getString("return_date")
                )) {
                    estimates.returnsSkipped++;
                } else {
                    estimates.returnsMerged++;
                }
            }
        }
    }

    private Map<Integer, AssignmentFingerprint> loadAssignmentFingerprints(Connection source) throws SQLException {
        Map<Integer, AssignmentFingerprint> map = new HashMap<>();
        try (PreparedStatement ps = source.prepareStatement(
                "SELECT id, person, department, equipment_type, reason, quantity, date FROM assignments"
        );
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getInt("id"), new AssignmentFingerprint(
                        rs.getString("person"),
                        rs.getString("department"),
                        rs.getString("equipment_type"),
                        rs.getString("reason"),
                        rs.getInt("quantity"),
                        rs.getString("date")
                ));
            }
        }
        return map;
    }

    private PublishResult mergeSubmissionIntoPublishedDatabase(Path submissionFile, Path publishedFile, String actor)
            throws Exception {
        MergeStats stats = new MergeStats();

        try (Connection target = openConnection(publishedFile);
             Connection source = openConnection(submissionFile)) {
            target.setAutoCommit(false);
            try {
                mergeDepartments(source, target, stats);
                mergeUsers(source, target, stats);
                mergeEquipment(source, target, stats);
                Map<Integer, Integer> assignmentMap = mergeAssignments(source, target, stats);
                mergeDistribution(source, target, assignmentMap, stats);
                mergeReturns(source, target, stats);
                mergeAuditLogs(source, target, stats);
                reconcileEquipmentStatuses(target);
                target.commit();
            } catch (Exception e) {
                target.rollback();
                throw e;
            } finally {
                target.setAutoCommit(true);
            }
        }

        AuditService.log(
                actor,
                "MERGE_SUBMISSION",
                "BACKUP_SYNC",
                stats.toSummary()
        );

        return new PublishResult(
                publishedFile,
                "Validated backup schema and conflict checks successfully.",
                stats.toSummary()
        );
    }

    private Connection openConnection(Path databaseFile) throws SQLException {
        Connection connection = DriverManager.getConnection(SQLITE_URL_PREFIX + databaseFile.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    private void validateSubmissionDatabase(Path submissionFile) throws Exception {
        try (Connection connection = openConnection(submissionFile)) {
            requireColumns(connection, "equipment",
                    "asset_code", "name", "category", "serial_number", "condition", "source", "entry_date", "status");
            requireColumns(connection, "assignments",
                    "id", "person", "department", "equipment_type", "reason", "quantity", "date");
            requireColumns(connection, "distribution",
                    "asset_code", "assignment_id", "assigned_to", "phone", "nid", "date", "returned");
            requireColumns(connection, "returns",
                    "asset_code", "returned_by", "phone", "nid", "condition", "remarks", "return_date");
        } catch (SQLException e) {
            throw new IOException("The submitted backup could not be opened as a valid SQLite database.", e);
        }
    }

    private void requireColumns(Connection connection, String tableName, String... requiredColumns) throws Exception {
        if (!tableExists(connection, tableName)) {
            throw new Exception("Backup validation failed. Missing table: " + tableName);
        }

        Set<String> columns = getColumns(connection, tableName);
        List<String> missing = new ArrayList<>();
        for (String requiredColumn : requiredColumns) {
            if (!columns.contains(requiredColumn.toLowerCase())) {
                missing.add(requiredColumn);
            }
        }

        if (!missing.isEmpty()) {
            throw new Exception(
                    "Backup validation failed. Table " + tableName + " is missing columns: " + String.join(", ", missing)
            );
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND LOWER(name) = LOWER(?) LIMIT 1"
        )) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Set<String> getColumns(Connection connection, String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                columns.add(normalize(rs.getString("name")));
            }
        }
        return columns;
    }

    private void mergeDepartments(Connection source, Connection target, MergeStats stats) throws SQLException {
        if (!tableExists(source, "departments")) {
            return;
        }

        try (PreparedStatement select = source.prepareStatement("SELECT name FROM departments");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (name == null || name.isBlank() || departmentExists(target, name)) {
                    continue;
                }

                try (PreparedStatement insert = target.prepareStatement("INSERT INTO departments(name) VALUES (?)")) {
                    insert.setString(1, name.trim());
                    insert.executeUpdate();
                    stats.departmentsMerged++;
                }
            }
        }
    }

    private boolean departmentExists(Connection connection, String department) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM departments WHERE LOWER(TRIM(name)) = LOWER(TRIM(?)) LIMIT 1"
        )) {
            ps.setString(1, department);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void mergeUsers(Connection source, Connection target, MergeStats stats) throws Exception {
        if (!tableExists(source, "users")) {
            return;
        }

        String sql = "SELECT full_name, username, password, role, status, department, phone, email, " +
                "reset_code, reset_expiry, reset_requested_at, last_password_reset, created_at FROM users";
        try (PreparedStatement ps = source.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String username = rs.getString("username");
                String email = rs.getString("email");
                String phone = rs.getString("phone");
                String department = rs.getString("department");

                if (department != null && !department.isBlank() && !departmentExists(target, department)) {
                    try (PreparedStatement insertDepartment = target.prepareStatement("INSERT INTO departments(name) VALUES (?)")) {
                        insertDepartment.setString(1, department.trim());
                        insertDepartment.executeUpdate();
                        stats.departmentsMerged++;
                    }
                }

                if (userExists(target, username, email)) {
                    stats.usersSkipped++;
                    continue;
                }

                if (phoneConflictExists(target, phone)) {
                    throw new Exception("User merge conflict detected for phone number: " + phone);
                }

                try (PreparedStatement insert = target.prepareStatement(
                        "INSERT INTO users (full_name, username, password, role, status, department, phone, email, " +
                                "reset_code, reset_expiry, reset_requested_at, last_password_reset, created_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                )) {
                    insert.setString(1, rs.getString("full_name"));
                    insert.setString(2, username);
                    insert.setString(3, rs.getString("password"));
                    insert.setString(4, rs.getString("role"));
                    insert.setString(5, rs.getString("status"));
                    insert.setString(6, department);
                    insert.setString(7, phone);
                    insert.setString(8, email);
                    insert.setString(9, rs.getString("reset_code"));
                    insert.setString(10, rs.getString("reset_expiry"));
                    insert.setString(11, rs.getString("reset_requested_at"));
                    insert.setString(12, rs.getString("last_password_reset"));
                    insert.setString(13, rs.getString("created_at"));
                    insert.executeUpdate();
                    stats.usersMerged++;
                }
            }
        }
    }

    private boolean userExists(Connection connection, String username, String email) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM users WHERE " +
                        "(? IS NOT NULL AND TRIM(?) <> '' AND LOWER(COALESCE(email, '')) = LOWER(?)) OR " +
                        "(? IS NOT NULL AND TRIM(?) <> '' AND LOWER(COALESCE(username, '')) = LOWER(?)) LIMIT 1"
        )) {
            ps.setString(1, email);
            ps.setString(2, email);
            ps.setString(3, email);
            ps.setString(4, username);
            ps.setString(5, username);
            ps.setString(6, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean phoneConflictExists(Connection connection, String phone) throws SQLException {
        if (phone == null || phone.isBlank()) {
            return false;
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM users WHERE COALESCE(TRIM(phone), '') = TRIM(?) LIMIT 1"
        )) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void mergeEquipment(Connection source, Connection target, MergeStats stats) throws Exception {
        String sql = "SELECT asset_code, name, category, serial_number, condition, source, entry_date, status FROM equipment";
        try (PreparedStatement ps = source.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String assetCode = rs.getString("asset_code");
                String serialNumber = rs.getString("serial_number");
                String name = rs.getString("name");
                String category = rs.getString("category");

                EquipmentSnapshot existingByAsset = findEquipmentByAssetCode(target, assetCode);
                if (existingByAsset != null) {
                    if (!sameEquipmentIdentity(existingByAsset, serialNumber, name, category)) {
                        throw new Exception("Equipment merge conflict detected for asset code " + assetCode + ".");
                    }
                    stats.equipmentSkipped++;
                    continue;
                }

                EquipmentSnapshot existingBySerial = findEquipmentBySerialNumber(target, serialNumber);
                if (existingBySerial != null && !normalize(existingBySerial.assetCode).equals(normalize(assetCode))) {
                    throw new Exception(
                            "Equipment merge conflict detected for serial number " + serialNumber +
                                    ". Existing asset code: " + existingBySerial.assetCode +
                                    ", incoming asset code: " + assetCode + "."
                    );
                }

                long insertedId;
                try (PreparedStatement insert = target.prepareStatement(
                        "INSERT INTO equipment (name, category, serial_number, condition, source, entry_date, status) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS
                )) {
                    insert.setString(1, name);
                    insert.setString(2, category);
                    insert.setString(3, serialNumber);
                    insert.setString(4, rs.getString("condition"));
                    insert.setString(5, rs.getString("source"));
                    insert.setString(6, rs.getString("entry_date"));
                    insert.setString(7, rs.getString("status"));
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Failed to retrieve inserted equipment id.");
                        }
                        insertedId = keys.getLong(1);
                    }
                }

                try (PreparedStatement updateCode = target.prepareStatement("UPDATE equipment SET asset_code = ? WHERE id = ?")) {
                    updateCode.setString(1, assetCode);
                    updateCode.setLong(2, insertedId);
                    updateCode.executeUpdate();
                }

                stats.equipmentMerged++;
            }
        }
    }

    private EquipmentSnapshot findEquipmentByAssetCode(Connection connection, String assetCode) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT asset_code, serial_number, name, category FROM equipment WHERE LOWER(TRIM(asset_code)) = LOWER(TRIM(?)) LIMIT 1"
        )) {
            ps.setString(1, assetCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new EquipmentSnapshot(
                        rs.getString("asset_code"),
                        rs.getString("serial_number"),
                        rs.getString("name"),
                        rs.getString("category")
                );
            }
        }
    }

    private EquipmentSnapshot findEquipmentBySerialNumber(Connection connection, String serialNumber) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT asset_code, serial_number, name, category FROM equipment WHERE LOWER(TRIM(serial_number)) = LOWER(TRIM(?)) LIMIT 1"
        )) {
            ps.setString(1, serialNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new EquipmentSnapshot(
                        rs.getString("asset_code"),
                        rs.getString("serial_number"),
                        rs.getString("name"),
                        rs.getString("category")
                );
            }
        }
    }

    private boolean sameEquipmentIdentity(EquipmentSnapshot existing, String serialNumber, String name, String category) {
        return normalize(existing.serialNumber).equals(normalize(serialNumber))
                && normalize(existing.name).equals(normalize(name))
                && normalize(existing.category).equals(normalize(category));
    }

    private Map<Integer, Integer> mergeAssignments(Connection source, Connection target, MergeStats stats) throws Exception {
        Map<Integer, Integer> assignmentMap = new HashMap<>();
        String sql = "SELECT id, person, department, equipment_type, reason, quantity, date FROM assignments";

        try (PreparedStatement ps = source.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int sourceId = rs.getInt("id");
                Integer existingId = findAssignmentId(
                        target,
                        rs.getString("person"),
                        rs.getString("department"),
                        rs.getString("equipment_type"),
                        rs.getString("reason"),
                        rs.getInt("quantity"),
                        rs.getString("date")
                );
                if (existingId != null) {
                    assignmentMap.put(sourceId, existingId);
                    stats.assignmentsSkipped++;
                    continue;
                }

                if (rs.getString("department") != null && !rs.getString("department").isBlank()
                        && !departmentExists(target, rs.getString("department"))) {
                    try (PreparedStatement insertDepartment = target.prepareStatement("INSERT INTO departments(name) VALUES (?)")) {
                        insertDepartment.setString(1, rs.getString("department").trim());
                        insertDepartment.executeUpdate();
                        stats.departmentsMerged++;
                    }
                }

                try (PreparedStatement insert = target.prepareStatement(
                        "INSERT INTO assignments (person, department, equipment_type, reason, quantity, date) VALUES (?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS
                )) {
                    insert.setString(1, rs.getString("person"));
                    insert.setString(2, rs.getString("department"));
                    insert.setString(3, rs.getString("equipment_type"));
                    insert.setString(4, rs.getString("reason"));
                    insert.setInt(5, rs.getInt("quantity"));
                    insert.setString(6, rs.getString("date"));
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Failed to retrieve inserted assignment id.");
                        }
                        assignmentMap.put(sourceId, keys.getInt(1));
                    }
                }
                stats.assignmentsMerged++;
            }
        }

        return assignmentMap;
    }

    private Integer findAssignmentId(Connection connection, String person, String department, String equipmentType,
                                     String reason, int quantity, String date) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM assignments WHERE " +
                        "LOWER(COALESCE(TRIM(person), '')) = LOWER(COALESCE(TRIM(?), '')) AND " +
                        "LOWER(COALESCE(TRIM(department), '')) = LOWER(COALESCE(TRIM(?), '')) AND " +
                        "LOWER(COALESCE(TRIM(equipment_type), '')) = LOWER(COALESCE(TRIM(?), '')) AND " +
                        "LOWER(COALESCE(TRIM(reason), '')) = LOWER(COALESCE(TRIM(?), '')) AND " +
                        "quantity = ? AND COALESCE(TRIM(date), '') = COALESCE(TRIM(?), '') LIMIT 1"
        )) {
            ps.setString(1, person);
            ps.setString(2, department);
            ps.setString(3, equipmentType);
            ps.setString(4, reason);
            ps.setInt(5, quantity);
            ps.setString(6, date);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
                return null;
            }
        }
    }

    private void mergeDistribution(Connection source, Connection target, Map<Integer, Integer> assignmentMap, MergeStats stats)
            throws Exception {
        String sql = "SELECT asset_code, assignment_id, assigned_to, phone, nid, date, returned FROM distribution";
        try (PreparedStatement ps = source.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String assetCode = rs.getString("asset_code");
                Integer targetAssignmentId = assignmentMap.get(rs.getInt("assignment_id"));
                if (targetAssignmentId == null) {
                    throw new Exception("Distribution merge failed. Assignment mapping was not found for asset " + assetCode + ".");
                }
                if (findEquipmentByAssetCode(target, assetCode) == null) {
                    throw new Exception("Distribution merge failed. Asset " + assetCode + " does not exist in the merged database.");
                }

                Integer existingId = findDistributionId(
                        target, assetCode, targetAssignmentId, rs.getString("assigned_to"),
                        rs.getString("phone"), rs.getString("nid"), rs.getString("date")
                );
                int returned = rs.getInt("returned");

                if (existingId != null) {
                    if (returned == 1) {
                        try (PreparedStatement update = target.prepareStatement("UPDATE distribution SET returned = 1 WHERE id = ?")) {
                            update.setInt(1, existingId);
                            update.executeUpdate();
                            stats.distributionUpdated++;
                        }
                    }
                    stats.distributionSkipped++;
                    continue;
                }

                if (returned == 0 && hasOpenDistributionConflict(target, assetCode)) {
                    throw new Exception(
                            "Distribution merge conflict detected. Asset " + assetCode +
                                    " is already assigned in the official database."
                    );
                }

                try (PreparedStatement insert = target.prepareStatement(
                        "INSERT INTO distribution (asset_code, assignment_id, assigned_to, phone, nid, date, returned) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?)"
                )) {
                    insert.setString(1, assetCode);
                    insert.setInt(2, targetAssignmentId);
                    insert.setString(3, rs.getString("assigned_to"));
                    insert.setString(4, rs.getString("phone"));
                    insert.setString(5, rs.getString("nid"));
                    insert.setString(6, rs.getString("date"));
                    insert.setInt(7, returned);
                    insert.executeUpdate();
                    stats.distributionMerged++;
                }
            }
        }
    }

    private Integer findDistributionId(Connection connection, String assetCode, int assignmentId, String assignedTo,
                                       String phone, String nid, String date) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM distribution WHERE " +
                        "LOWER(COALESCE(TRIM(asset_code), '')) = LOWER(COALESCE(TRIM(?), '')) AND " +
                        "assignment_id = ? AND " +
                        "LOWER(COALESCE(TRIM(assigned_to), '')) = LOWER(COALESCE(TRIM(?), '')) AND " +
                        "LOWER(COALESCE(TRIM(phone), '')) = LOWER(COALESCE(TRIM(?), '')) AND " +
                        "LOWER(COALESCE(TRIM(nid), '')) = LOWER(COALESCE(TRIM(?), '')) AND " +
                        "COALESCE(TRIM(date), '') = COALESCE(TRIM(?), '') LIMIT 1"
        )) {
            ps.setString(1, assetCode);
            ps.setInt(2, assignmentId);
            ps.setString(3, assignedTo);
            ps.setString(4, phone);
            ps.setString(5, nid);
            ps.setString(6, date);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("id") : null;
            }
        }
    }

    private boolean hasOpenDistributionConflict(Connection connection, String assetCode) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM distribution WHERE LOWER(TRIM(asset_code)) = LOWER(TRIM(?)) AND returned = 0 LIMIT 1"
        )) {
            ps.setString(1, assetCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void mergeReturns(Connection source, Connection target, MergeStats stats) throws Exception {
        String sql = "SELECT asset_code, returned_by, phone, nid, condition, remarks, return_date FROM returns";
        try (PreparedStatement ps = source.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String assetCode = rs.getString("asset_code");
                if (findEquipmentByAssetCode(target, assetCode) == null) {
                    throw new Exception("Return merge failed. Asset " + assetCode + " does not exist in the merged database.");
                }

                if (returnExists(
                        target,
                        assetCode,
                        rs.getString("returned_by"),
                        rs.getString("phone"),
                        rs.getString("nid"),
                        rs.getString("condition"),
                        rs.getString("remarks"),
                        rs.getString("return_date")
                )) {
                    stats.returnsSkipped++;
                    continue;
                }

                try (PreparedStatement insert = target.prepareStatement(
                        "INSERT INTO returns (asset_code, returned_by, phone, nid, condition, remarks, return_date) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?)"
                )) {
                    insert.setString(1, assetCode);
                    insert.setString(2, rs.getString("returned_by"));
                    insert.setString(3, rs.getString("phone"));
                    insert.setString(4, rs.getString("nid"));
                    insert.setString(5, rs.getString("condition"));
                    insert.setString(6, rs.getString("remarks"));
                    insert.setString(7, rs.getString("return_date"));
                    insert.executeUpdate();
                    stats.returnsMerged++;
                }
            }
        }
    }

    private boolean returnExists(Connection connection, String assetCode, String returnedBy, String phone, String nid,
                                 String condition, String remarks, String returnDate) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM returns WHERE " +
                        "LOWER(COALESCE(TRIM(asset_code), '')) = LOWER(COALESCE(TRIM(?), '')) AND " +
                        "LOWER(COALESCE(TRIM(returned_by), '')) = LOWER(COALESCE(TRIM(?), '')) AND " +
                        "LOWER(COALESCE(TRIM(phone), '')) = LOWER(COALESCE(TRIM(?), '')) AND " +
                        "LOWER(COALESCE(TRIM(nid), '')) = LOWER(COALESCE(TRIM(?), '')) AND " +
                        "LOWER(COALESCE(TRIM(condition), '')) = LOWER(COALESCE(TRIM(?), '')) AND " +
                        "LOWER(COALESCE(TRIM(remarks), '')) = LOWER(COALESCE(TRIM(?), '')) AND " +
                        "COALESCE(TRIM(return_date), '') = COALESCE(TRIM(?), '') LIMIT 1"
        )) {
            ps.setString(1, assetCode);
            ps.setString(2, returnedBy);
            ps.setString(3, phone);
            ps.setString(4, nid);
            ps.setString(5, condition);
            ps.setString(6, remarks);
            ps.setString(7, returnDate);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void mergeAuditLogs(Connection source, Connection target, MergeStats stats) throws Exception {
        if (!tableExists(source, "audit_log")) {
            return;
        }

        Set<String> sourceColumns = getColumns(source, "audit_log");
        String usernameColumn = sourceColumns.contains("username") ? "username" : "performed_by";
        String moduleColumn = sourceColumns.contains("module_name") ? "module_name" : "entity";

        String sql = "SELECT " + usernameColumn + " AS log_username, action, " + moduleColumn +
                " AS log_module, details, action_time, performed_by FROM audit_log";

        try (PreparedStatement ps = source.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (auditLogExists(
                        target,
                        rs.getString("log_username"),
                        rs.getString("action"),
                        rs.getString("log_module"),
                        rs.getString("details"),
                        rs.getString("action_time")
                )) {
                    stats.auditLogsSkipped++;
                    continue;
                }

                try (PreparedStatement insert = target.prepareStatement(
                        "INSERT INTO audit_log (username, action, module_name, details, action_time, performed_by) " +
                                "VALUES (?, ?, ?, ?, ?, ?)"
                )) {
                    insert.setString(1, rs.getString("log_username"));
                    insert.setString(2, rs.getString("action"));
                    insert.setString(3, rs.getString("log_module"));
                    insert.setString(4, rs.getString("details"));
                    insert.setString(5, rs.getString("action_time"));
                    insert.setString(6, rs.getString("performed_by"));
                    insert.executeUpdate();
                    stats.auditLogsMerged++;
                }
            }
        }
    }

    private boolean auditLogExists(Connection connection, String username, String action, String moduleName,
                                   String details, String actionTime) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM audit_log WHERE " +
                        "LOWER(COALESCE(TRIM(username), '')) = LOWER(COALESCE(TRIM(?), '')) AND " +
                        "LOWER(COALESCE(TRIM(action), '')) = LOWER(COALESCE(TRIM(?), '')) AND " +
                        "LOWER(COALESCE(TRIM(module_name), '')) = LOWER(COALESCE(TRIM(?), '')) AND " +
                        "LOWER(COALESCE(TRIM(details), '')) = LOWER(COALESCE(TRIM(?), '')) AND " +
                        "COALESCE(TRIM(action_time), '') = COALESCE(TRIM(?), '') LIMIT 1"
        )) {
            ps.setString(1, username);
            ps.setString(2, action);
            ps.setString(3, moduleName);
            ps.setString(4, details);
            ps.setString(5, actionTime);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void reconcileEquipmentStatuses(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "UPDATE equipment SET status = 'ASSIGNED' " +
                            "WHERE asset_code IN (SELECT DISTINCT asset_code FROM distribution WHERE returned = 0)"
            );
            statement.executeUpdate(
                    "UPDATE equipment SET status = 'AVAILABLE' " +
                            "WHERE status = 'ASSIGNED' " +
                            "AND asset_code NOT IN (SELECT DISTINCT asset_code FROM distribution WHERE returned = 0)"
            );
            statement.executeUpdate(
                    "UPDATE equipment SET condition = (" +
                            "SELECT r.condition FROM returns r " +
                            "WHERE r.asset_code = equipment.asset_code " +
                            "AND COALESCE(TRIM(r.condition), '') <> '' " +
                            "ORDER BY COALESCE(r.return_date, '') DESC, r.id DESC LIMIT 1" +
                            ") WHERE EXISTS (" +
                            "SELECT 1 FROM returns r WHERE r.asset_code = equipment.asset_code " +
                            "AND COALESCE(TRIM(r.condition), '') <> ''" +
                            ")"
            );
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public static final class RestoreResult {
        private final Path officialFile;
        private final Path safetyBackupFile;

        public RestoreResult(Path officialFile, Path safetyBackupFile) {
            this.officialFile = officialFile;
            this.safetyBackupFile = safetyBackupFile;
        }

        public Path getOfficialFile() {
            return officialFile;
        }

        public Path getSafetyBackupFile() {
            return safetyBackupFile;
        }
    }

    public static final class DemoResetResult {
        private final Path safetyBackupFile;
        private final Map<String, Integer> clearedRows;

        public DemoResetResult(Path safetyBackupFile, Map<String, Integer> clearedRows) {
            this.safetyBackupFile = safetyBackupFile;
            this.clearedRows = Map.copyOf(clearedRows);
        }

        public Path getSafetyBackupFile() {
            return safetyBackupFile;
        }

        public Map<String, Integer> getClearedRows() {
            return clearedRows;
        }

        public String toDisplayText() {
            StringBuilder builder = new StringBuilder();
            builder.append("Safety backup: ").append(safetyBackupFile.getFileName()).append(System.lineSeparator());
            builder.append("Cleared tables:").append(System.lineSeparator());
            for (Map.Entry<String, Integer> entry : clearedRows.entrySet()) {
                builder.append(" - ")
                        .append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue())
                        .append(System.lineSeparator());
            }
            builder.append("User accounts, departments, and backup files were preserved.");
            return builder.toString();
        }
    }

    public static final class PublishResult {
        private final Path publishedFile;
        private final String validationSummary;
        private final String mergeSummary;

        public PublishResult(Path publishedFile, String validationSummary, String mergeSummary) {
            this.publishedFile = publishedFile;
            this.validationSummary = validationSummary;
            this.mergeSummary = mergeSummary;
        }

        public Path getPublishedFile() {
            return publishedFile;
        }

        public String getValidationSummary() {
            return validationSummary;
        }

        public String getMergeSummary() {
            return mergeSummary;
        }
    }

    public static final class CompatibilityReport {
        private final Path submissionFile;
        private final String officialVersion;
        private final int submissionUsers;
        private final int submissionEquipment;
        private final int submissionAssignments;
        private final int submissionDistributions;
        private final int submissionReturns;
        private final boolean safeToMerge;
        private final List<String> conflicts;
        private final String mergeEstimate;

        public CompatibilityReport(Path submissionFile, String officialVersion, int submissionUsers,
                                   int submissionEquipment, int submissionAssignments, int submissionDistributions,
                                   int submissionReturns, boolean safeToMerge, List<String> conflicts,
                                   String mergeEstimate) {
            this.submissionFile = submissionFile;
            this.officialVersion = officialVersion;
            this.submissionUsers = submissionUsers;
            this.submissionEquipment = submissionEquipment;
            this.submissionAssignments = submissionAssignments;
            this.submissionDistributions = submissionDistributions;
            this.submissionReturns = submissionReturns;
            this.safeToMerge = safeToMerge;
            this.conflicts = new ArrayList<>(conflicts);
            this.mergeEstimate = mergeEstimate;
        }

        public String toDisplayText() {
            StringBuilder builder = new StringBuilder();
            builder.append("Submission File: ").append(submissionFile.getFileName()).append("\n");
            builder.append("Latest Official: ").append(officialVersion).append("\n\n");
            builder.append("Submission Contents\n");
            builder.append("Users: ").append(submissionUsers).append("\n");
            builder.append("Equipment: ").append(submissionEquipment).append("\n");
            builder.append("Assignments: ").append(submissionAssignments).append("\n");
            builder.append("Distributions: ").append(submissionDistributions).append("\n");
            builder.append("Returns: ").append(submissionReturns).append("\n\n");
            builder.append("Merge Estimate\n").append(mergeEstimate).append("\n\n");
            if (conflicts.isEmpty()) {
                builder.append("Compatibility Result\nSafe to merge. No blocking conflicts were detected.");
            } else {
                builder.append("Compatibility Result\nBlocked by conflicts:\n");
                for (String conflict : conflicts) {
                    builder.append("- ").append(conflict).append("\n");
                }
            }
            return builder.toString().trim();
        }

        public boolean isSafeToMerge() {
            return safeToMerge;
        }

        public String getOfficialVersion() {
            return officialVersion;
        }

        public int getConflictCount() {
            return conflicts.size();
        }
    }

    private static final class EquipmentSnapshot {
        private final String assetCode;
        private final String serialNumber;
        private final String name;
        private final String category;

        private EquipmentSnapshot(String assetCode, String serialNumber, String name, String category) {
            this.assetCode = assetCode;
            this.serialNumber = serialNumber;
            this.name = name;
            this.category = category;
        }
    }

    private static final class AssignmentFingerprint {
        private final String person;
        private final String department;
        private final String equipmentType;
        private final String reason;
        private final int quantity;
        private final String date;

        private AssignmentFingerprint(String person, String department, String equipmentType,
                                      String reason, int quantity, String date) {
            this.person = person;
            this.department = department;
            this.equipmentType = equipmentType;
            this.reason = reason;
            this.quantity = quantity;
            this.date = date;
        }
    }

    private static final class MergeStats {
        private int departmentsMerged;
        private int usersMerged;
        private int usersSkipped;
        private int equipmentMerged;
        private int equipmentSkipped;
        private int assignmentsMerged;
        private int assignmentsSkipped;
        private int distributionMerged;
        private int distributionSkipped;
        private int distributionUpdated;
        private int returnsMerged;
        private int returnsSkipped;
        private int auditLogsMerged;
        private int auditLogsSkipped;

        private String toSummary() {
            return "Merged departments: " + departmentsMerged +
                    ", users: " + usersMerged + " (skipped " + usersSkipped + ")" +
                    ", equipment: " + equipmentMerged + " (skipped " + equipmentSkipped + ")" +
                    ", assignments: " + assignmentsMerged + " (skipped " + assignmentsSkipped + ")" +
                    ", distributions: " + distributionMerged + " (skipped " + distributionSkipped + ", updated " + distributionUpdated + ")" +
                    ", returns: " + returnsMerged + " (skipped " + returnsSkipped + ")" +
                    ", audit logs: " + auditLogsMerged + " (skipped " + auditLogsSkipped + ").";
        }
    }
}
