package com.mycompany.msr.amis.api.dto.sync;

import com.fasterxml.jackson.databind.JsonNode;

public record SyncConflictResolveRequest(
        long conflictId,
        String resolutionAction,
        JsonNode mergedPayload
) {
}
