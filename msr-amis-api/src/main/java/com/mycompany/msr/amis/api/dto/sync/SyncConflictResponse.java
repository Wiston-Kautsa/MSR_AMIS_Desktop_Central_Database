package com.mycompany.msr.amis.api.dto.sync;

import com.fasterxml.jackson.databind.JsonNode;

public record SyncConflictResponse(
        long id,
        String entityType,
        String entityId,
        JsonNode localPayload,
        JsonNode centralPayload,
        String conflictType,
        String resolutionStatus,
        String resolutionAction,
        String resolvedBy,
        String resolvedAt,
        String createdAt
) {
}
