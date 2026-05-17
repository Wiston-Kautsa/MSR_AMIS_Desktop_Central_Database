package com.mycompany.msr.amis.api.dto.sync;

public record SyncPushItemResponse(
        Long queueId,
        String entityType,
        String entityId,
        String operation,
        String status,
        String message
) {
}
