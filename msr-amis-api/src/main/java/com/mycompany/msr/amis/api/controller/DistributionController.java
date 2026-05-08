package com.mycompany.msr.amis.api.controller;

import com.mycompany.msr.amis.api.dto.distribution.DistributionBatchRequest;
import com.mycompany.msr.amis.api.dto.distribution.DistributionResponse;
import com.mycompany.msr.amis.api.service.OperationsService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/distributions")
public class DistributionController {

    private final OperationsService operationsService;

    public DistributionController(OperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @GetMapping("/current")
    public List<DistributionResponse> getCurrentDistributions() {
        return operationsService.getCurrentDistributions();
    }

    @GetMapping("/available-equipment")
    public List<String> getAvailableEquipment(@RequestParam(required = false) String category) {
        return operationsService.getAvailableEquipment(category);
    }

    @GetMapping("/asset/{assetCode}")
    public DistributionResponse getCurrentDistributionForAsset(@PathVariable String assetCode) {
        return operationsService.getCurrentDistributionForAsset(assetCode);
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public void distributeBatch(Authentication authentication, @Valid @RequestBody DistributionBatchRequest request) {
        operationsService.distributeBatch(authentication.getName(), request);
    }
}
