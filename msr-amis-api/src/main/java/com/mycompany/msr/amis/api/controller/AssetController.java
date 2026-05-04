package com.mycompany.msr.amis.api.controller;

import com.mycompany.msr.amis.api.dto.asset.AssetHistoryResponse;
import com.mycompany.msr.amis.api.service.AuditHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AuditHistoryService auditHistoryService;

    public AssetController(AuditHistoryService auditHistoryService) {
        this.auditHistoryService = auditHistoryService;
    }

    @GetMapping("/{assetCode}/history")
    public AssetHistoryResponse getAssetHistory(@PathVariable String assetCode) {
        return auditHistoryService.getAssetHistory(assetCode);
    }
}
