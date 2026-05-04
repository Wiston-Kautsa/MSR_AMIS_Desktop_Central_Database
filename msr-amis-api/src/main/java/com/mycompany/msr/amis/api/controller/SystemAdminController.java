package com.mycompany.msr.amis.api.controller;

import com.mycompany.msr.amis.api.dto.CommonMessageResponse;
import com.mycompany.msr.amis.api.service.SystemAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemAdminController {

    private final SystemAdminService systemAdminService;

    public SystemAdminController(SystemAdminService systemAdminService) {
        this.systemAdminService = systemAdminService;
    }

    @GetMapping("/data-maintenance")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SystemAdminService.DataMaintenanceSummary getDataMaintenanceSummary() {
        return systemAdminService.getSummary();
    }

    @PostMapping("/data-maintenance/reset")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CommonMessageResponse resetComponent(Authentication authentication,
                                                @RequestBody DataMaintenanceResetRequest request) {
        String message = systemAdminService.resetComponent(authentication.getName(), request.component());
        return new CommonMessageResponse(true, message);
    }

    public record DataMaintenanceResetRequest(String component) {
    }
}
