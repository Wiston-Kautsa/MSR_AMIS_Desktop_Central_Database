package com.mycompany.msr.amis.api.dto.sync;

public record SyncValidationIssueResponse(
        String severity,
        String category,
        String entityType,
        String entityId,
        String message
) {
}
