package com.mycompany.msr.amis.api.dto.sync;

public record SyncAuditLogResponse(
        long id,
        String syncSessionToken,
        String userId,
        String machineId,
        String action,
        String entityType,
        int recordCount,
        String status,
        String errorMessage,
        Long durationMs,
        String createdAt
) {
}
