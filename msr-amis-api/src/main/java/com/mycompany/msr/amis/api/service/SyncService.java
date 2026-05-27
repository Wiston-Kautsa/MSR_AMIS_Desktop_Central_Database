package com.mycompany.msr.amis.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mycompany.msr.amis.api.domain.UserAccount;
import com.mycompany.msr.amis.api.dto.CommonMessageResponse;
import com.mycompany.msr.amis.api.dto.assignment.AssignmentRequest;
import com.mycompany.msr.amis.api.dto.assignment.AssignmentResponse;
import com.mycompany.msr.amis.api.dto.distribution.DistributionBatchRequest;
import com.mycompany.msr.amis.api.dto.distribution.DistributionRequest;
import com.mycompany.msr.amis.api.dto.returns.ReturnBatchRequest;
import com.mycompany.msr.amis.api.dto.returns.ReturnItemRequest;
import com.mycompany.msr.amis.api.dto.sync.SyncPushRequest;
import com.mycompany.msr.amis.api.dto.sync.SyncPushItemResponse;
import com.mycompany.msr.amis.api.dto.sync.SyncPushResponse;
import com.mycompany.msr.amis.api.dto.sync.SyncQueueRecordRequest;
import com.mycompany.msr.amis.api.dto.sync.SyncRetryRequest;
import com.mycompany.msr.amis.api.dto.sync.SyncStatusResponse;
import com.mycompany.msr.amis.api.dto.sync.SyncValidationIssueResponse;
import com.mycompany.msr.amis.api.dto.user.UserRequest;
import com.mycompany.msr.amis.api.exception.ApiException;
import com.mycompany.msr.amis.api.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final OperationsService operationsService;
    private final UserManagementService userManagementService;
    private final DepartmentService departmentService;
    private final UserRepository userRepository;

    public SyncService(JdbcTemplate jdbcTemplate,
                       SyncQueueService syncQueueService,
                       SyncAuditService syncAuditService,
                       SyncConflictService syncConflictService,
                       SyncLockService syncLockService,
                       SyncValidationService syncValidationService,
                       EquipmentSyncHandler equipmentSyncHandler,
                       OperationsService operationsService,
                       UserManagementService userManagementService,
                       DepartmentService departmentService,
                       UserRepository userRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.syncQueueService = syncQueueService;
        this.syncAuditService = syncAuditService;
        this.syncConflictService = syncConflictService;
        this.syncLockService = syncLockService;
        this.syncValidationService = syncValidationService;
        this.equipmentSyncHandler = equipmentSyncHandler;
        this.operationsService = operationsService;
        this.userManagementService = userManagementService;
        this.departmentService = departmentService;
        this.userRepository = userRepository;
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
        String entityType = normalize(record.entityType()).toUpperCase(Locale.ROOT);
        if (syncQueueService.alreadyProcessed(record.idempotencyKey())) {
            return "Already processed";
        }
        String message = switch (entityType) {
            case "EQUIPMENT" -> equipmentSyncHandler.apply(actor, machineId, record);
            case "ASSIGNMENT" -> applyAssignment(actor, record);
            case "DISTRIBUTION" -> applyDistribution(actor, record);
            case "RETURN" -> applyReturn(actor, record);
            case "USER" -> applyUser(actor, record);
            case "DEPARTMENT" -> applyDepartment(actor, record);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported sync entity: " + record.entityType());
        };
        if (!"EQUIPMENT".equals(entityType)) {
            syncQueueService.markIdempotencyKeyProcessed(
                    actor,
                    machineId,
                    record.idempotencyKey(),
                    entityType,
                    record.entityId(),
                    record.operation(),
                    record.payload()
            );
        }
        return message;
    }

    private String applyDepartment(String actor, SyncQueueRecordRequest record) {
        JsonNode payload = requirePayload(record);
        String operation = operation(record);
        switch (operation) {
            case "CREATE" -> departmentService.createDepartment(actor, text(payload, "name"));
            case "UPDATE" -> departmentService.updateDepartment(actor, text(payload, "oldName"), text(payload, "name"));
            case "DELETE" -> departmentService.deleteDepartment(actor, firstNonBlank(text(payload, "name"), record.entityId()));
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported department sync operation: " + operation);
        }
        return "Department " + operation.toLowerCase(Locale.ROOT) + " applied";
    }

    private String applyAssignment(String actor, SyncQueueRecordRequest record) {
        JsonNode payload = requirePayload(record);
        String operation = operation(record);
        switch (operation) {
            case "CREATE" -> operationsService.createAssignment(actor, assignmentRequest(payload));
            case "UPDATE" -> operationsService.updateAssignment(actor, resolveAssignmentId(record), assignmentRequest(payload));
            case "STATUS" -> operationsService.updateAssignmentStatus(actor, resolveAssignmentId(record), text(payload, "status"));
            case "DELETE" -> operationsService.deleteAssignment(actor, resolveAssignmentId(record));
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported assignment sync operation: " + operation);
        }
        return "Assignment " + operation.toLowerCase(Locale.ROOT) + " applied";
    }

    private String applyDistribution(String actor, SyncQueueRecordRequest record) {
        JsonNode payload = requirePayload(record);
        int assignmentId = resolveAssignmentId(payload.path("assignment"), record.entityId());
        List<DistributionRequest> distributions = new ArrayList<>();
        for (JsonNode item : payload.path("items")) {
            distributions.add(new DistributionRequest(
                    resolveAssetCode(item),
                    text(item, "assignedTo"),
                    text(item, "phone"),
                    text(item, "nid")
            ));
        }
        operationsService.distributeBatch(actor, new DistributionBatchRequest(assignmentId, distributions));
        return "Distribution sync applied";
    }

    private String applyReturn(String actor, SyncQueueRecordRequest record) {
        JsonNode payload = requirePayload(record);
        int assignmentId = resolveAssignmentId(payload.path("assignment"), record.entityId());
        List<ReturnItemRequest> items = new ArrayList<>();
        for (JsonNode item : payload.path("items")) {
            items.add(new ReturnItemRequest(
                    firstNonBlank(text(item, "originalAssetCode"), resolveAssetCode(item)),
                    text(item, "enteredIdentifier"),
                    text(item, "returnedBy"),
                    text(item, "phone"),
                    text(item, "nid"),
                    text(item, "condition"),
                    text(item, "remarks"),
                    item.path("replacement").asBoolean(false)
            ));
        }
        operationsService.completeReturns(actor, new ReturnBatchRequest(
                assignmentId,
                text(payload, "equipmentType"),
                text(payload, "outstandingRemark"),
                stringMap(payload.path("outstandingRemarks")),
                items
        ));
        return "Return sync applied";
    }

    private String applyUser(String actor, SyncQueueRecordRequest record) {
        JsonNode payload = requirePayload(record);
        String operation = operation(record);
        switch (operation) {
            case "CREATE" -> userManagementService.createUser(actor, userRequest(payload));
            case "UPDATE" -> userManagementService.updateUser(actor, resolveUserId(record), userRequest(payload));
            case "STATUS" -> userManagementService.updateStatus(actor, resolveUserId(record), text(payload, "status"));
            case "DELETE" -> userManagementService.deleteUser(actor, resolveUserId(record));
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported user sync operation: " + operation);
        }
        return "User " + operation.toLowerCase(Locale.ROOT) + " applied";
    }

    private AssignmentRequest assignmentRequest(JsonNode payload) {
        return new AssignmentRequest(
                text(payload, "person"),
                text(payload, "department"),
                text(payload, "equipmentType"),
                text(payload, "reason"),
                payload.path("quantity").asInt()
        );
    }

    private UserRequest userRequest(JsonNode payload) {
        String email = text(payload, "email");
        return new UserRequest(
                text(payload, "name"),
                firstNonBlank(text(payload, "username"), email.toLowerCase(Locale.ROOT)),
                email,
                text(payload, "role"),
                text(payload, "department"),
                text(payload, "phone"),
                text(payload, "password")
        );
    }

    private int resolveAssignmentId(SyncQueueRecordRequest record) {
        return resolveAssignmentId(requirePayload(record), record.entityId());
    }

    private int resolveAssignmentId(JsonNode payload, String fallbackId) {
        int directId = firstPositive(payload.path("id").asInt(0), parseInt(fallbackId));
        if (directId > 0) {
            return directId;
        }
        String person = text(payload, "person");
        String department = text(payload, "department");
        String equipmentType = text(payload, "equipmentType");
        String reason = text(payload, "reason");
        int quantity = payload.path("quantity").asInt(0);
        for (AssignmentResponse assignment : operationsService.getAssignments()) {
            if (same(person, assignment.person())
                    && same(department, assignment.department())
                    && same(equipmentType, assignment.equipmentType())
                    && same(reason, assignment.reason())
                    && quantity == assignment.quantity()) {
                return assignment.id();
            }
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "Assignment could not be resolved for sync.");
    }

    private long resolveUserId(SyncQueueRecordRequest record) {
        JsonNode payload = requirePayload(record);
        long directId = firstPositive(payload.path("id").asLong(0), parseLong(record.entityId()));
        if (directId > 0) {
            return directId;
        }
        String email = firstNonBlank(text(payload, "email"), text(record.baseline(), "email"));
        UserAccount user = userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(email, email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User could not be resolved for sync."));
        return user.getId();
    }

    private String resolveAssetCode(JsonNode item) {
        String assetCode = firstNonBlank(text(item, "assetCode"), text(item, "originalAssetCode"));
        if (!assetCode.isBlank()) {
            return assetCode;
        }
        String serialNumber = text(item, "serialNumber");
        if (serialNumber.isBlank()) {
            return "";
        }
        return jdbcTemplate.query(
                "SELECT asset_code FROM equipment WHERE LOWER(TRIM(serial_number))=LOWER(TRIM(?)) LIMIT 1",
                (rs, rowNum) -> rs.getString("asset_code"),
                serialNumber
        ).stream().findFirst().orElse("");
    }

    private JsonNode requirePayload(SyncQueueRecordRequest record) {
        JsonNode payload = record.payload();
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payload is required.");
        }
        return payload;
    }

    private String operation(SyncQueueRecordRequest record) {
        return normalize(record.operation()).toUpperCase(Locale.ROOT);
    }

    private Map<String, String> stringMap(JsonNode node) {
        Map<String, String> values = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue().asText("")));
        }
        return values;
    }

    private String text(JsonNode payload, String field) {
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return "";
        }
        JsonNode value = payload.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private boolean same(String first, String second) {
        return normalize(first).equalsIgnoreCase(normalize(second));
    }

    private int firstPositive(int first, int second) {
        return first > 0 ? first : second;
    }

    private long firstPositive(long first, long second) {
        return first > 0 ? first : second;
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(normalize(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(normalize(value));
        } catch (Exception ignored) {
            return 0L;
        }
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
