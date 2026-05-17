package com.mycompany.msr.amis.api.service;

import com.mycompany.msr.amis.api.dto.CommonMessageResponse;
import com.mycompany.msr.amis.api.dto.sync.SyncPushRequest;
import com.mycompany.msr.amis.api.dto.sync.SyncPushItemResponse;
import com.mycompany.msr.amis.api.dto.sync.SyncPushResponse;
import com.mycompany.msr.amis.api.dto.sync.SyncQueueRecordRequest;
import com.mycompany.msr.amis.api.dto.sync.SyncRetryRequest;
import com.mycompany.msr.amis.api.dto.sync.SyncStatusResponse;
import com.mycompany.msr.amis.api.dto.sync.SyncValidationIssueResponse;
import com.mycompany.msr.amis.api.exception.ApiException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SyncService {

    private final JdbcTemplate jdbcTemplate;
    private final SyncQueueService syncQueueService;
    private final SyncAuditService syncAuditService;
    private final SyncConflictService syncConflictService;
    private final SyncLockService syncLockService;
    private final SyncValidationService syncValidationService;
    private final EquipmentSyncHandler equipmentSyncHandler;

    public SyncService(JdbcTemplate jdbcTemplate,
                       SyncQueueService syncQueueService,
                       SyncAuditService syncAuditService,
                       SyncConflictService syncConflictService,
                       SyncLockService syncLockService,
                       SyncValidationService syncValidationService,
                       EquipmentSyncHandler equipmentSyncHandler) {
        this.jdbcTemplate = jdbcTemplate;
        this.syncQueueService = syncQueueService;
        this.syncAuditService = syncAuditService;
        this.syncConflictService = syncConflictService;
        this.syncLockService = syncLockService;
        this.syncValidationService = syncValidationService;
        this.equipmentSyncHandler = equipmentSyncHandler;
    }

    public SyncPushResponse pushNow(String actor, SyncPushRequest request) {
        SyncPushRequest safeRequest = request == null
                ? new SyncPushRequest(null, null, null, false, List.of(), List.of())
                : request;
        String sessionToken = firstNonBlank(safeRequest.sessionToken(), "SYNC-" + System.currentTimeMillis());
        String machineId = firstNonBlank(safeRequest.machineId(), "UNKNOWN_MACHINE");
        long started = System.currentTimeMillis();
        Long sessionId = createSession(sessionToken, actor, machineId, safeRequest.machineName());
        int total = 0;
        int applied = 0;
        int failed = 0;
        int conflicts = 0;
        int quarantined = 0;
        List<SyncPushItemResponse> results = new ArrayList<>();
        syncLockService.acquire(actor, machineId, sessionToken, settingInt("sync.lock_ttl_seconds", 900));
        try {
            for (SyncQueueRecordRequest record : safeRecords(safeRequest.records())) {
                total++;
                long queueId = 0L;
                try {
                    queueId = syncQueueService.enqueue(sessionId, actor, machineId, record);
                    List<SyncValidationIssueResponse> issues = syncValidationService.validateRecord(record);
                    if (syncValidationService.hasBlockingIssues(issues)) {
                        String message = summarizeIssues(issues);
                        syncQueueService.markFailed(queueId, message);
                        failed++;
                        results.add(result(queueId, record, "FAILED", message));
                        continue;
                    }
                    if (safeRequest.dryRun()) {
                        syncQueueService.markApplied(queueId);
                        applied++;
                        results.add(result(queueId, record, "APPLIED", "Dry run validated. No PostgreSQL write performed."));
                        continue;
                    }

                    String message = processRecord(actor, machineId, record);
                    syncQueueService.markApplied(queueId);
                    applied++;
                    results.add(result(queueId, record, "APPLIED", message));
                } catch (Exception itemException) {
                    String message = itemException.getMessage() == null ? "Sync item failed." : itemException.getMessage();
                    if (queueId > 0) {
                        syncQueueService.markFailed(queueId, message);
                    }
                    failed++;
                    results.add(result(queueId == 0 ? null : queueId, record, "FAILED", message));
                }
            }
            String responseStatus = pushStatus(total, applied, failed, conflicts, quarantined);
            completeSession(sessionId, failed == total && total > 0 ? "FAILED" : "COMPLETED");
            syncAuditService.log(sessionId, actor, machineId, safeRequest.dryRun() ? "SYNC_DRY_RUN" : "SYNC_PUSH",
                    "SYNC", total, failed == 0 ? "SUCCESS" : responseStatus, failed == 0 ? "" : failed + " record(s) failed.",
                    System.currentTimeMillis() - started);
            return new SyncPushResponse(sessionToken, responseStatus, total, applied, failed, conflicts, quarantined,
                    "Push processed " + total + " sync record(s).", results);
        } catch (Exception exception) {
            completeSession(sessionId, "FAILED");
            syncAuditService.log(sessionId, actor, machineId, "SYNC_PUSH", "SYNC", total, "FAILED",
                    exception.getMessage(), System.currentTimeMillis() - started);
            if (exception instanceof ApiException apiException) {
                throw apiException;
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } finally {
            syncLockService.release(sessionToken);
        }
    }

    private String processRecord(String actor, String machineId, SyncQueueRecordRequest record) {
        String entityType = normalize(record.entityType()).toUpperCase();
        if ("EQUIPMENT".equals(entityType)) {
            return equipmentSyncHandler.apply(actor, machineId, record);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported sync entity: " + record.entityType());
    }

    private SyncPushItemResponse result(Long queueId, SyncQueueRecordRequest record, String status, String message) {
        return new SyncPushItemResponse(
                queueId,
                normalize(record.entityType()),
                normalize(record.entityId()),
                normalize(record.operation()),
                status,
                message
        );
    }

    private String pushStatus(int total, int applied, int failed, int conflicts, int quarantined) {
        if (total == 0) {
            return "NO_RECORDS";
        }
        if (applied == total) {
            return "SUCCESS";
        }
        if (failed == total) {
            return "FAILED";
        }
        return "PARTIAL_SUCCESS";
    }

    public CommonMessageResponse pullNow(String actor, String machineId) {
        syncAuditService.log(null, actor, machineId, "SYNC_PULL", "SYNC", 0, "SUCCESS",
                "Pull request accepted. Desktop should refresh central queue, status, and audit views.", 0L);
        return new CommonMessageResponse(true, "Pull completed. Central sync status is ready to refresh.");
    }

    public SyncStatusResponse getStatus() {
        SyncLockService.LockState lockState = syncLockService.getState();
        return new SyncStatusResponse(
                "UP",
                "CONNECTED",
                currentSchemaVersion(),
                OffsetDateTime.now().toString(),
                lockState.active(),
                lockState.lockedBy(),
                syncQueueService.countByStatus("PENDING"),
                syncQueueService.countByStatus("FAILED"),
                syncConflictService.openConflictCount(),
                syncQueueService.countByStatus("QUARANTINED"),
                syncAuditService.lastSyncAt("SUCCESS"),
                syncAuditService.lastSyncAt("FAILED")
        );
    }

    public CommonMessageResponse retryFailed(String actor, SyncRetryRequest request, boolean superAdmin) {
        int retried = syncQueueService.retryFailed(request == null ? List.of() : request.queueIds(), actor, superAdmin);
        syncAuditService.log(null, actor, "", "SYNC_RETRY", "SYNC", retried, "SUCCESS", "", 0L);
        return new CommonMessageResponse(true, "Moved " + retried + " sync record(s) back to pending.");
    }

    public CommonMessageResponse clearCompletedLogs(String actor) {
        int cleared = syncQueueService.clearCompletedLogs();
        syncAuditService.log(null, actor, "", "SYNC_COMPLETED_LOGS_CLEARED", "SYNC", cleared, "SUCCESS", "", 0L);
        return new CommonMessageResponse(true, "Cleared " + cleared + " completed sync record(s).");
    }

    public CommonMessageResponse clearQueue(String actor) {
        int cleared = syncQueueService.clearQueue();
        syncAuditService.log(null, actor, "", "SYNC_QUEUE_CLEARED", "SYNC", cleared, "SUCCESS", "", 0L);
        return new CommonMessageResponse(true, "Cleared " + cleared + " central sync queue record(s).");
    }

    public CommonMessageResponse resetSyncState(String actor) {
        int cleared = syncQueueService.resetSyncState();
        syncAuditService.log(null, actor, "", "SYNC_STATE_RESET", "SYNC", cleared, "SUCCESS", "", 0L);
        return new CommonMessageResponse(true, "Reset central sync state. Removed " + cleared + " sync record(s).");
    }

    public CommonMessageResponse forceReleaseSyncLock(String actor) {
        syncLockService.forceRelease(actor);
        syncAuditService.log(null, actor, "", "SYNC_LOCK_FORCE_RELEASED", "SYNC", 1, "SUCCESS", "", 0L);
        return new CommonMessageResponse(true, "Central sync lock released.");
    }

    private Long createSession(String sessionToken, String actor, String machineId, String machineName) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO sync_sessions(session_token, started_by, machine_id, machine_name, status) " +
                        "VALUES (?, ?, ?, ?, 'RUNNING') ON CONFLICT(session_token) DO UPDATE SET status='RUNNING' RETURNING id",
                Long.class,
                sessionToken,
                actor,
                machineId,
                firstNonBlank(machineName, machineId)
        );
    }

    private void completeSession(Long sessionId, String status) {
        jdbcTemplate.update(
                "UPDATE sync_sessions SET status=?, ended_at=CURRENT_TIMESTAMP WHERE id=?",
                status,
                sessionId
        );
    }

    private String currentSchemaVersion() {
        return jdbcTemplate.query(
                "SELECT version FROM flyway_schema_history WHERE success=true ORDER BY installed_rank DESC LIMIT 1",
                (rs, rowNum) -> rs.getString("version")
        ).stream().findFirst().orElse("");
    }

    private int settingInt(String key, int fallback) {
        return jdbcTemplate.query(
                "SELECT value FROM sync_settings WHERE key=?",
                (rs, rowNum) -> rs.getString("value"),
                key
        ).stream().findFirst().map(value -> {
            try {
                return Integer.parseInt(value);
            } catch (Exception ignored) {
                return fallback;
            }
        }).orElse(fallback);
    }

    private List<SyncQueueRecordRequest> safeRecords(List<SyncQueueRecordRequest> records) {
        return records == null ? new ArrayList<>() : records;
    }

    private String summarizeIssues(List<SyncValidationIssueResponse> issues) {
        return issues.stream()
                .map(SyncValidationIssueResponse::message)
                .reduce((left, right) -> left + " | " + right)
                .orElse("Validation failed.");
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
