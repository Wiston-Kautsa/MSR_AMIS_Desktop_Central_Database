package com.mycompany.msr.amis.api.controller;

import com.mycompany.msr.amis.api.dto.CommonMessageResponse;
import com.mycompany.msr.amis.api.dto.audit.AuditLogRequest;
import com.mycompany.msr.amis.api.dto.audit.AuditLogResponse;
import com.mycompany.msr.amis.api.service.AuditHistoryService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditController {

    private final AuditHistoryService auditHistoryService;

    public AuditController(AuditHistoryService auditHistoryService) {
        this.auditHistoryService = auditHistoryService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<AuditLogResponse> getAuditLogs(Authentication authentication,
                                               @RequestParam(required = false) String username) {
        return auditHistoryService.getAuditLogs(authentication.getName(), username);
    }

    @PostMapping
    public CommonMessageResponse logAudit(Authentication authentication, @RequestBody AuditLogRequest request) {
        return auditHistoryService.logAuditEntry(authentication.getName(), request);
    }
}
