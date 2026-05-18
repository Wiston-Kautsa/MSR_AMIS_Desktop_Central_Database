package com.mycompany.msr.amis.api.controller;

import com.mycompany.msr.amis.api.dto.maintenance.MaintenanceRequest;
import com.mycompany.msr.amis.api.dto.maintenance.MaintenanceResponse;
import com.mycompany.msr.amis.api.service.MaintenanceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping
    public List<MaintenanceResponse> getMaintenanceRecords() {
        return maintenanceService.getMaintenanceRecords();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public MaintenanceResponse create(Authentication authentication, @Valid @RequestBody MaintenanceRequest request) {
        return maintenanceService.createMaintenanceRecord(authentication.getName(), request);
    }
}
