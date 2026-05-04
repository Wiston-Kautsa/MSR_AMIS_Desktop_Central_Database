package com.mycompany.msr.amis.api.dto.audit;

public record AuditLogRequest(
        String username,
        String action,
        String moduleName,
        String details
) {
}
