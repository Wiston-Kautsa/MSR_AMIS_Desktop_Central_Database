package com.mycompany.msr.amis.api.controller;

import com.mycompany.msr.amis.api.dto.CommonMessageResponse;
import com.mycompany.msr.amis.api.dto.sync.EquipmentSyncRequest;
import com.mycompany.msr.amis.api.dto.sync.SyncAuditLogResponse;
import com.mycompany.msr.amis.api.dto.sync.SyncConflictResolveRequest;
import com.mycompany.msr.amis.api.dto.sync.SyncConflictResponse;
import com.mycompany.msr.amis.api.dto.sync.SyncPushRequest;
import com.mycompany.msr.amis.api.dto.sync.SyncPushResponse;
import com.mycompany.msr.amis.api.dto.sync.SyncQueueRecordResponse;
import com.mycompany.msr.amis.api.dto.sync.SyncRetryRequest;
import com.mycompany.msr.amis.api.dto.sync.SyncStatusResponse;
import com.mycompany.msr.amis.api.service.SyncAuditService;
import com.mycompany.msr.amis.api.service.SyncConflictService;
import com.mycompany.msr.amis.api.service.EquipmentFacadeService;
import com.mycompany.msr.amis.api.service.SyncService;
import com.mycompany.msr.amis.api.service.SyncQueueService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;
    private final SyncAuditService syncAuditService;
    private final SyncConflictService syncConflictService;
    private final SyncQueueService syncQueueService;
    private final EquipmentFacadeService equipmentFacadeService;

    public SyncController(SyncService syncService,
                          SyncAuditService syncAuditService,
                          SyncConflictService syncConflictService,
                          SyncQueueService syncQueueService,
                          EquipmentFacadeService equipmentFacadeService) {
        this.syncService = syncService;
        this.syncAuditService = syncAuditService;
        this.syncConflictService = syncConflictService;
        this.syncQueueService = syncQueueService;
        this.equipmentFacadeService = equipmentFacadeService;
    }

    @PostMapping("/push")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SyncPushResponse push(Authentication authentication, @RequestBody SyncPushRequest request) {
        return syncService.pushNow(authentication.getName(), request);
    }

    @PostMapping("/equipment")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<String> syncEquipment(Authentication authentication,
                                                @RequestBody EquipmentSyncRequest request) {
        if (syncQueueService.alreadyProcessed(request.idempotencyKey())) {
            return ResponseEntity.ok("Already processed");
        }
        equipmentFacadeService.syncEquipment(authentication.getName(), request);
        syncQueueService.markIdempotencyKeyProcessed(
                authentication.getName(),
                "",
                request.idempotencyKey(),
                "EQUIPMENT",
                request.assetCode(),
                "UPDATE",
                request
        );
        return ResponseEntity.ok("Equipment synced successfully");
    }

    @GetMapping("/pull")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CommonMessageResponse pull(Authentication authentication,
                                      @RequestParam(required = false) String machineId) {
        return syncService.pullNow(authentication.getName(), machineId);
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SyncStatusResponse status() {
        return syncService.getStatus();
    }

    @GetMapping("/queue")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<SyncQueueRecordResponse> queue(@RequestParam(required = false) String entityType,
                                               @RequestParam(required = false) String status) {
        return syncQueueService.getQueueRecords(entityType, status);
    }

    @PostMapping("/retry")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CommonMessageResponse retry(Authentication authentication, @RequestBody(required = false) SyncRetryRequest request) {
        boolean superAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
        return syncService.retryFailed(authentication.getName(), request, superAdmin);
    }

    @PostMapping("/queue/clear-completed")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CommonMessageResponse clearCompletedLogs(Authentication authentication) {
        return syncService.clearCompletedLogs(authentication.getName());
    }

    @PostMapping("/queue/clear")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CommonMessageResponse clearQueue(Authentication authentication) {
        return syncService.clearQueue(authentication.getName());
    }

    @PostMapping("/reset")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CommonMessageResponse resetSyncState(Authentication authentication) {
        return syncService.resetSyncState(authentication.getName());
    }

    @PostMapping("/lock/force-release")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CommonMessageResponse forceReleaseSyncLock(Authentication authentication) {
        return syncService.forceReleaseSyncLock(authentication.getName());
    }

    @GetMapping("/conflicts")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<SyncConflictResponse> conflicts(@RequestParam(required = false) String entityType,
                                                @RequestParam(required = false) String status) {
        return syncConflictService.getConflicts(entityType, status);
    }

    @PostMapping("/conflicts/resolve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CommonMessageResponse resolveConflict(Authentication authentication,
                                                 @RequestBody SyncConflictResolveRequest request) {
        return syncConflictService.resolve(authentication.getName(), request);
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<SyncAuditLogResponse> audit(@RequestParam(required = false) String userId,
                                            @RequestParam(required = false) String machineId,
                                            @RequestParam(required = false) String entityType,
                                            @RequestParam(required = false) String status) {
        return syncAuditService.getLogs(userId, machineId, entityType, status);
    }
}
