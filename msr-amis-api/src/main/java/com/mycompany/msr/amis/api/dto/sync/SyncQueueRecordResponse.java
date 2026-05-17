package com.mycompany.msr.amis.api.dto.sync;

public record SyncQueueRecordResponse(
        long id,
        String entityType,
        String operation,
        String entityId,
        String createdBy,
        String status,
        int retryCount,
        String machineId,
        String idempotencyKey,
        String description,
        String errorMessage,
        String createdAt,
        String processedAt
) {
}
