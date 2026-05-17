package com.mycompany.msr.amis.api.dto.sync;

import com.fasterxml.jackson.databind.JsonNode;

public record SyncQueueRecordRequest(
        String entityType,
        String entityId,
        String operation,
        String idempotencyKey,
        String checksum,
        JsonNode payload,
        JsonNode baseline
) {
}
