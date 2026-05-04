package com.mycompany.msr.amis;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class BackupSyncConfigService {

    private static final String CONFIG_FILE_NAME = "backup-sync.properties";
    private static final String OFFICIAL_PATH_KEY = "official.path";
    private static final String SUBMISSIONS_PATH_KEY = "submissions.path";
    private static final String LOCAL_BACKUP_PATH_KEY = "local_backup.path";
    private static final String LOCAL_DB_PATH_KEY = "local_db.path";

    private BackupSyncConfigService() {
    }

    public static BackupSyncConfig load() throws IOException {
        Properties properties = new Properties();
        Path configPath = FileLocationHelper.resolveAppDataFile(CONFIG_FILE_NAME);

        if (Files.exists(configPath)) {
            try (InputStream inputStream = Files.newInputStream(configPath)) {
                properties.load(inputStream);
            }
        }

        Path officialPath = resolvePath(
                properties.getProperty(OFFICIAL_PATH_KEY),
                defaultSharedRoot().resolve("OFFICIAL")
        );
        Path submissionsPath = resolvePath(
                properties.getProperty(SUBMISSIONS_PATH_KEY),
                defaultSharedRoot().resolve("SUBMISSIONS")
        );
        Path localBackupPath = resolvePath(
                properties.getProperty(LOCAL_BACKUP_PATH_KEY),
                defaultLocalBackupRoot()
        );
        Path localDbPath = resolvePath(
                properties.getProperty(LOCAL_DB_PATH_KEY),
                FileLocationHelper.resolveCanonicalDataFile("msr_amis.db")
        );

        Files.createDirectories(officialPath);
        Files.createDirectories(submissionsPath);
        Files.createDirectories(localBackupPath);
        if (localDbPath.getParent() != null) {
            Files.createDirectories(localDbPath.getParent());
        }

        properties.setProperty(OFFICIAL_PATH_KEY, officialPath.toString());
        properties.setProperty(SUBMISSIONS_PATH_KEY, submissionsPath.toString());
        properties.setProperty(LOCAL_BACKUP_PATH_KEY, localBackupPath.toString());
        properties.setProperty(LOCAL_DB_PATH_KEY, localDbPath.toString());

        try (OutputStream outputStream = Files.newOutputStream(configPath)) {
            properties.store(outputStream, "MSR AMIS Backup & Sync Paths");
        }

        return new BackupSyncConfig(officialPath, submissionsPath, localBackupPath, localDbPath);
    }

    private static Path resolvePath(String configuredValue, Path fallback) {
        if (configuredValue != null && !configuredValue.isBlank()) {
            return Paths.get(configuredValue).toAbsolutePath().normalize();
        }
        return fallback.toAbsolutePath().normalize();
    }

    private static Path defaultSharedRoot() {
        String configured = System.getenv("MSR_AMIS_SHARED_ROOT");
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured).toAbsolutePath().normalize();
        }

        Path googleDriveRoot = Paths.get("G:\\My Drive\\MSR-AMIS-DATA");
        if (Files.exists(googleDriveRoot)) {
            return googleDriveRoot;
        }

        return Paths.get(System.getProperty("user.home"), "Documents", "MSR-AMIS-DATA")
                .toAbsolutePath()
                .normalize();
    }

    private static Path defaultLocalBackupRoot() {
        return Paths.get(System.getProperty("user.home"), "Documents", "MSR-AMIS", "backups")
                .toAbsolutePath()
                .normalize();
    }
}
