package com.mycompany.msr.amis.api.dto.audit;

public record AuditLogResponse(
        int id,
        String username,
        String action,
        String moduleName,
        String details,
        String actionTime
) {
}
