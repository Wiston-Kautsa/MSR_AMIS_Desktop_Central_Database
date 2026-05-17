package com.mycompany.msr.amis.api.dto.sync;

public record SyncStatusResponse(
        String apiStatus,
        String databaseStatus,
        String schemaVersion,
        String serverTime,
        boolean lockActive,
        String lockedBy,
        long pendingCount,
        long failedCount,
        long conflictCount,
        long quarantinedCount,
        String lastSuccessfulSync,
        String lastFailedSync
) {
}
