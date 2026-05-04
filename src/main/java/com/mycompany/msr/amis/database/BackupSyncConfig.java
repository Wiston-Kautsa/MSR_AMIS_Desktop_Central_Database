package com.mycompany.msr.amis;

import java.nio.file.Path;

public final class BackupSyncConfig {

    private final Path officialPath;
    private final Path submissionsPath;
    private final Path localBackupPath;
    private final Path localDatabasePath;

    public BackupSyncConfig(Path officialPath, Path submissionsPath, Path localBackupPath, Path localDatabasePath) {
        this.officialPath = officialPath;
        this.submissionsPath = submissionsPath;
        this.localBackupPath = localBackupPath;
        this.localDatabasePath = localDatabasePath;
    }

    public Path getOfficialPath() {
        return officialPath;
    }

    public Path getSubmissionsPath() {
        return submissionsPath;
    }

    public Path getLocalBackupPath() {
        return localBackupPath;
    }

    public Path getLocalDatabasePath() {
        return localDatabasePath;
    }
}
