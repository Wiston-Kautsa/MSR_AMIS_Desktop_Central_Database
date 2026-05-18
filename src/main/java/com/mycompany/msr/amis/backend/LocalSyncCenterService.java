package com.mycompany.msr.amis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LocalSyncCenterService implements SyncCenterService {

    private final SyncStorageRepository storageRepository = new SyncStorageRepository();
    private final RemoteMirrorCoordinator remoteMirrorCoordinator = ServiceRegistry.getRemoteMirrorCoordinator();

    @Override
    public SyncCenterSummary getSummary() throws Exception {
        int pending = 0;
        int applied = 0;
        int rejected = 0;
        int failed = 0;
        for (SyncQueueRecord record : getQueueRecords()) {
            switch (normalize(record.getStatus())) {
                case "APPLIED":
                    applied++;
                    break;
                case "REJECTED":
                    rejected++;
                    break;
                case "FAILED":
                    failed++;
                    break;
                default:
                    pending++;
                    break;
            }
        }
        boolean onlineReady = remoteMirrorCoordinator.hasRemoteSession();
        String message = onlineReady
                ? "Central session is available. Local changes will be pushed to the Central Server, then fresh central data will be pulled back to the desktop."
                : "Central session is not available. Local changes will stay queued until the app is online.";
        return new SyncCenterSummary(pending, applied, rejected, failed, onlineReady, message);
    }

    @Override
    public List<SyncQueueRecord> getQueueRecords() throws Exception {
        try (Connection connection = DatabaseHandler.getConnection()) {
            List<SyncQueueRecord> records = new ArrayList<>();
            for (SyncStorageRepository.StoredSyncQueueItem item : storageRepository.loadQueueItems(connection)) {
                records.add(new SyncQueueRecord(
                        item.getId(),
                        item.getEntityType(),
                        item.getOperationType(),
                        item.getEntityKey(),
                        item.getActor(),
                        item.getStatus(),
                        item.getRetryCount(),
                        item.getMachineId(),
                        item.getIdempotencyKey(),
                        item.getDescription(),
                        item.getErrorMessage(),
                        item.getCreatedAt(),
                        item.getProcessedAt()
                ));
            }
            return records;
        }
    }

    @Override
    public List<SyncAuditRecord> getAuditRecords() throws Exception {
        try (Connection connection = DatabaseHandler.getConnection()) {
            return storageRepository.loadAuditRecords(connection);
        }
    }

    @Override
    public long queueOperation(String entityType,
                               String operationType,
                               String entityKey,
                               Object payload,
                               Object baselineSnapshot,
                               String description) throws Exception {
        String actor = currentActor();
        try (Connection connection = DatabaseHandler.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long queueId = storageRepository.insertQueueRecord(
                        connection,
                        entityType,
                        operationType,
                        entityKey,
                        payload,
                        baselineSnapshot,
                        actor,
                        description
                );
                storageRepository.insertAuditRecord(
                        connection,
                        queueId,
                        actor,
                        "OFFLINE_CHANGE_CAPTURED",
                        "CAPTURED",
                        normalize(description)
                );
                DatabaseHandler.logAudit(
                        "OFFLINE_CHANGE_CAPTURED",
                        "SYNC_QUEUE",
                        Long.toString(queueId),
                        normalize(description)
                );
                connection.commit();
                return queueId;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public String processPendingQueue() throws Exception {
        return processPendingQueue(null);
    }

    @Override
    public String processPendingQueueForActor(String actor) throws Exception {
        String normalizedActor = normalize(actor);
        if (normalizedActor.isBlank()) {
            throw new IllegalArgumentException("A sync actor is required.");
        }
        return processPendingQueue(normalizedActor);
    }

    @Override
    public String pushPendingEquipment() throws Exception {
        return pushPendingEquipment(null);
    }

    @Override
    public String pushPendingEquipmentForActor(String actor) throws Exception {
        String normalizedActor = normalize(actor);
        if (normalizedActor.isBlank()) {
            throw new IllegalArgumentException("A sync actor is required.");
        }
        return pushPendingEquipment(normalizedActor);
    }

    private String pushPendingEquipment(String actorScope) throws Exception {
        if (!remoteMirrorCoordinator.hasRemoteSession()) {
            throw new IllegalStateException("A live central session is required before pending equipment can be synced.");
        }

        int applied = 0;
        int failed = 0;
        String sessionId = "SYNC-" + UUID.randomUUID();
        try (Connection connection = DatabaseHandler.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!storageRepository.tryAcquireLock(connection, currentActor(), sessionId)) {
                    SyncLockInfo lockInfo = storageRepository.loadLockInfo(connection);
                    throw new IllegalStateException("Sync Lock is ACTIVE. Locked by: "
                            + lockInfo.getLockedBy() + ". Started: " + lockInfo.getStartedAt() + ".");
                }
                connection.commit();
                connection.setAutoCommit(false);
                for (SyncStorageRepository.StoredSyncQueueItem item : orderedQueueItems(storageRepository.loadQueueItems(connection))) {
                    if (!"EQUIPMENT".equalsIgnoreCase(normalize(item.getEntityType()))) {
                        continue;
                    }
                    String status = normalize(item.getStatus());
                    if (!"PENDING".equalsIgnoreCase(status)) {
                        continue;
                    }
                    if (actorScope != null && !actorScope.equalsIgnoreCase(normalize(item.getActor()))) {
                        continue;
                    }

                    try {
                        postEquipmentSync(item);
                        storageRepository.markApplied(connection, item.getId(), "Equipment pushed to central API.");
                        storageRepository.insertAuditRecord(
                                connection,
                                item.getId(),
                                item.getActor(),
                                "EQUIPMENT_SYNC_APPLIED",
                                "APPLIED",
                                "Queued equipment action was applied to the central API."
                        );
                        applied++;
                    } catch (Exception exception) {
                        String message = safeMessage(exception);
                        storageRepository.markFailed(connection, item.getId(), message);
                        storageRepository.insertAuditRecord(
                                connection,
                                item.getId(),
                                item.getActor(),
                                "EQUIPMENT_SYNC_FAILED",
                                "FAILED",
                                message
                        );
                        failed++;
                    }
                    connection.commit();
                    connection.setAutoCommit(false);
                }
            } finally {
                try {
                    storageRepository.releaseLock(connection, sessionId);
                    connection.commit();
                } catch (Exception ignored) {
                    // Best effort cleanup; Super Admin can force-release if the process dies.
                }
                connection.setAutoCommit(true);
            }
        }

        remoteMirrorCoordinator.synchronizeFromRemote(Map.of());
        return "Equipment push finished. Synced: " + applied + ". Failed: " + failed + ".";
    }

    private void postEquipmentSync(SyncStorageRepository.StoredSyncQueueItem item) throws Exception {
        ApiClient apiClient = remoteMirrorCoordinator.getApiClient();
        if (apiClient == null) {
            throw new IllegalStateException("Central API client is not configured.");
        }
        apiClient.post("/api/sync/equipment", buildEquipmentSyncRequest(item), String.class);
    }

    private ObjectNode buildEquipmentSyncRequest(SyncStorageRepository.StoredSyncQueueItem item) {
        ObjectNode request = item.getPayload() != null && item.getPayload().isObject()
                ? ((ObjectNode) item.getPayload()).deepCopy()
                : JsonNodeFactory.instance.objectNode();

        putIfMissing(request, "assetCode", item.getEntityKey());
        putIfMissing(request, "operation", item.getOperationType());
        putIfMissing(request, "idempotencyKey", item.getIdempotencyKey());
        return request;
    }

    private void putIfMissing(ObjectNode node, String fieldName, String value) {
        if (node.hasNonNull(fieldName) || value == null || value.isBlank()) {
            return;
        }
        node.put(fieldName, value);
    }

    private String processPendingQueue(String actorScope) throws Exception {
        if (!remoteMirrorCoordinator.hasRemoteSession()) {
            throw new IllegalStateException("A live central session is required before pending offline actions can be synced.");
        }
        List<SyncValidationIssue> validationIssues = validateBeforeSync();
        long blockingIssues = validationIssues.stream()
                .filter(issue -> "ERROR".equalsIgnoreCase(issue.getSeverity()))
                .count();
        if (blockingIssues > 0) {
            throw new IllegalStateException("Pre-sync validation failed: " + blockingIssues
                    + " issue(s). Open Validation Issues before pushing.");
        }

        int applied = 0;
        int rejected = 0;
        int failed = 0;
        String sessionId = "SYNC-" + UUID.randomUUID();

        try (Connection connection = DatabaseHandler.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!storageRepository.tryAcquireLock(connection, currentActor(), sessionId)) {
                    SyncLockInfo lockInfo = storageRepository.loadLockInfo(connection);
                    throw new IllegalStateException("Sync Lock is ACTIVE. Locked by: "
                            + lockInfo.getLockedBy() + ". Started: " + lockInfo.getStartedAt() + ".");
                }
                connection.commit();
                connection.setAutoCommit(false);
                for (SyncStorageRepository.StoredSyncQueueItem item : orderedQueueItems(storageRepository.loadQueueItems(connection))) {
                    String status = normalize(item.getStatus());
                    if (!"PENDING".equals(status) && !"FAILED".equals(status)) {
                        continue;
                    }
                    if (actorScope != null && !actorScope.equalsIgnoreCase(normalize(item.getActor()))) {
                        continue;
                    }
                    String policyViolation = policyViolation(item);
                    if (!policyViolation.isBlank()) {
                        storageRepository.markQuarantined(connection, item.getId(), policyViolation);
                        storageRepository.insertAuditRecord(
                                connection,
                                item.getId(),
                                item.getActor(),
                                "SYNC_QUARANTINED_POLICY",
                                "QUARANTINED",
                                policyViolation
                        );
                        rejected++;
                        connection.commit();
                        connection.setAutoCommit(false);
                        continue;
                    }

                    try {
                        RemoteMirrorCoordinator.runWithAutoMirrorSuppressed(() -> applyQueueItem(item));
                        storageRepository.markApplied(connection, item.getId(), "Applied to central API.");
                        storageRepository.insertAuditRecord(
                                connection,
                                item.getId(),
                                item.getActor(),
                                "SYNC_APPLIED",
                                "APPLIED",
                                "Queued action was applied to the central API."
                        );
                        DatabaseHandler.logAudit(
                                "SYNC_APPLIED",
                                "SYNC_QUEUE",
                                Long.toString(item.getId()),
                                item.getDescription()
                        );
                        applied++;
                    } catch (SyncConflictException conflictException) {
                        storageRepository.markRejected(connection, item.getId(), conflictException.getMessage());
                        storageRepository.insertAuditRecord(
                                connection,
                                item.getId(),
                                item.getActor(),
                                "SYNC_REJECTED_CONFLICT",
                                "REJECTED",
                                conflictException.getMessage()
                        );
                        DatabaseHandler.logAudit(
                                "SYNC_REJECTED_CONFLICT",
                                "SYNC_QUEUE",
                                Long.toString(item.getId()),
                                conflictException.getMessage()
                        );
                        rejected++;
                    } catch (ApiClientException apiException) {
                        if (isBusinessRejection(apiException)) {
                            storageRepository.markRejected(connection, item.getId(), apiException.getMessage());
                            storageRepository.insertAuditRecord(
                                    connection,
                                    item.getId(),
                                    item.getActor(),
                                    "SYNC_REJECTED_BY_CENTRAL_API",
                                    "REJECTED",
                                    apiException.getMessage()
                            );
                            DatabaseHandler.logAudit(
                                    "SYNC_REJECTED_BY_CENTRAL_API",
                                    "SYNC_QUEUE",
                                    Long.toString(item.getId()),
                                    apiException.getMessage()
                            );
                            rejected++;
                        } else {
                            storageRepository.markFailed(connection, item.getId(), safeMessage(apiException));
                            storageRepository.insertAuditRecord(
                                    connection,
                                    item.getId(),
                                    item.getActor(),
                                    "SYNC_FAILED",
                                    "FAILED",
                                    safeMessage(apiException)
                            );
                            failed++;
                        }
                    } catch (Exception exception) {
                        storageRepository.markFailed(connection, item.getId(), safeMessage(exception));
                        storageRepository.insertAuditRecord(
                                connection,
                                item.getId(),
                                item.getActor(),
                                "SYNC_FAILED",
                                "FAILED",
                                safeMessage(exception)
                        );
                        failed++;
                    }

                    connection.commit();
                    connection.setAutoCommit(false);
                }
            } finally {
                try {
                    storageRepository.releaseLock(connection, sessionId);
                } catch (Exception ignored) {
                    // Best effort cleanup; Super Admin can force-release if the process dies.
                }
                connection.setAutoCommit(true);
            }
        }

        remoteMirrorCoordinator.synchronizeFromRemote(Map.of());

        return "Sync finished. Pushed to Central Server: " + applied + ". Rejected: " + rejected + ". Failed: " + failed
                + ". Desktop data refreshed from the Central Server.";
    }

    @Override
    public String retryRejectedQueue() throws Exception {
        try (Connection connection = DatabaseHandler.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int retried = storageRepository.retryRejectedAndFailed(connection, currentActor());
                connection.commit();
                return retried == 0
                        ? "There are no rejected or failed sync items to retry."
                        : "Moved " + retried + " rejected or failed sync item(s) back to pending.";
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public String pullFromCentralServer() throws Exception {
        if (!remoteMirrorCoordinator.hasRemoteSession()) {
            throw new IllegalStateException("A live central session is required before pulling from the Central Server.");
        }
        remoteMirrorCoordinator.synchronizeFromRemote(Map.of());
        return "Desktop data refreshed from the Central Server.";
    }

    @Override
    public String clearCompletedLogs(String actor) throws Exception {
        try (Connection connection = DatabaseHandler.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int cleared = storageRepository.clearCompleted(connection, actor);
                connection.commit();
                return cleared == 0
                        ? "No completed sync records were available to clear."
                        : "Cleared " + cleared + " completed sync record(s).";
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public String clearQueue(String actor) throws Exception {
        try (Connection connection = DatabaseHandler.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int cleared = storageRepository.clearQueue(connection, actor);
                connection.commit();
                return "Cleared " + cleared + " sync queue record(s).";
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public String resetSyncState(String actor) throws Exception {
        try (Connection connection = DatabaseHandler.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int cleared = storageRepository.resetSyncState(connection, actor);
                connection.commit();
                return "Reset sync state. Cleared " + cleared + " queue/audit record(s).";
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public List<SyncValidationIssue> validateBeforeSync() throws Exception {
        List<SyncValidationIssue> issues = new ArrayList<>();
        try (Connection connection = DatabaseHandler.getConnection()) {
            collectDuplicateIssues(connection, issues, "equipment", "asset_code", "Equipment", "Duplicate asset code");
            collectDuplicateIssues(connection, issues, "equipment", "serial_number", "Equipment", "Duplicate serial number");
            collectMissingIssues(connection, issues, "equipment", "asset_code", "Equipment", "Missing asset code");
            collectMissingIssues(connection, issues, "equipment", "serial_number", "Equipment", "Missing serial number");
            collectMissingIssues(connection, issues, "equipment", "name", "Equipment", "Missing asset name");
            collectMissingIssues(connection, issues, "assignments", "person", "Assignment", "Missing officer/person");
            collectInvalidAssignments(connection, issues);
            collectInvalidReturns(connection, issues);
            collectInvalidContacts(connection, issues);
        }
        return issues;
    }

    @Override
    public String runDryRun(Set<String> entityTypes) throws Exception {
        Set<String> allowed = normalizeEntityScope(entityTypes);
        int creates = 0;
        int updates = 0;
        int deletes = 0;
        int statusChanges = 0;
        int skipped = 0;
        int destructive = 0;
        try (Connection connection = DatabaseHandler.getConnection()) {
            for (SyncStorageRepository.StoredSyncQueueItem item : storageRepository.loadQueueItems(connection)) {
                if (!"PENDING".equals(normalize(item.getStatus())) && !"FAILED".equals(normalize(item.getStatus()))) {
                    continue;
                }
                if (!allowed.isEmpty() && !allowed.contains(normalize(item.getEntityType()))) {
                    skipped++;
                    continue;
                }
                switch (normalize(item.getOperationType())) {
                    case "CREATE":
                        creates++;
                        break;
                    case "UPDATE":
                        updates++;
                        break;
                    case "DELETE":
                        deletes++;
                        destructive++;
                        break;
                    case "STATUS":
                        statusChanges++;
                        if (isDestructiveStatus(item)) {
                            destructive++;
                        }
                        break;
                    default:
                        updates++;
                        break;
                }
            }
        }
        List<SyncValidationIssue> issues = validateBeforeSync();
        return "Simulation Result:\n"
                + creates + " inserts\n"
                + updates + " updates\n"
                + statusChanges + " status changes\n"
                + deletes + " deletes\n"
                + issues.size() + " validation issue(s)\n"
                + skipped + " skipped by scope\n"
                + destructive + " destructive action(s)";
    }

    @Override
    public SyncLockInfo getSyncLockInfo() throws Exception {
        try (Connection connection = DatabaseHandler.getConnection()) {
            return storageRepository.loadLockInfo(connection);
        }
    }

    @Override
    public String forceReleaseSyncLock(String actor) throws Exception {
        try (Connection connection = DatabaseHandler.getConnection()) {
            connection.setAutoCommit(false);
            try {
                storageRepository.forceReleaseLock(connection, actor);
                connection.commit();
                return "Sync lock released.";
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private List<SyncStorageRepository.StoredSyncQueueItem> orderedQueueItems(List<SyncStorageRepository.StoredSyncQueueItem> items) {
        return items.stream()
                .sorted(Comparator
                        .comparingInt((SyncStorageRepository.StoredSyncQueueItem item) -> dependencyOrder(item.getEntityType()))
                        .thenComparingLong(SyncStorageRepository.StoredSyncQueueItem::getId))
                .collect(java.util.stream.Collectors.toList());
    }

    private int dependencyOrder(String entityType) {
        switch (normalize(entityType)) {
            case "USER":
                return 10;
            case "DEPARTMENT":
                return 20;
            case "EQUIPMENT":
                return 30;
            case "ASSIGNMENT":
                return 40;
            case "DISTRIBUTION":
                return 50;
            case "RETURN":
                return 60;
            default:
                return 90;
        }
    }

    private String policyViolation(SyncStorageRepository.StoredSyncQueueItem item) {
        if ("REJECTED".equals(normalize(item.getStatus())) || "QUARANTINED".equals(normalize(item.getStatus()))) {
            return "Rejected and quarantined records are excluded by sync policy.";
        }
        JsonNode payload = item.getPayload();
        switch (normalize(item.getEntityType())) {
            case "USER":
                if (!isBlank(payload.path("status").asText()) && !"ACTIVE".equalsIgnoreCase(payload.path("status").asText())) {
                    return "Sync policy blocks non-active users.";
                }
                break;
            case "ASSIGNMENT":
                if ("DRAFT".equalsIgnoreCase(payload.path("status").asText())) {
                    return "Sync policy blocks draft assignments.";
                }
                break;
            case "RETURN":
                if ("DRAFT".equalsIgnoreCase(payload.path("status").asText())) {
                    return "Sync policy blocks draft returns.";
                }
                break;
            default:
                break;
        }
        return "";
    }

    private void collectDuplicateIssues(Connection connection,
                                        List<SyncValidationIssue> issues,
                                        String table,
                                        String column,
                                        String entity,
                                        String message) throws Exception {
        String sql = "SELECT " + column + " AS value, COUNT(*) AS count FROM " + table
                + " WHERE " + column + " IS NOT NULL AND TRIM(" + column + ") <> ''"
                + " GROUP BY LOWER(TRIM(" + column + ")) HAVING COUNT(*) > 1";
        try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                issues.add(new SyncValidationIssue("ERROR", "DUPLICATE", entity, rs.getString("value"),
                        message + " appears " + rs.getInt("count") + " time(s)."));
            }
        }
    }

    private void collectMissingIssues(Connection connection,
                                      List<SyncValidationIssue> issues,
                                      String table,
                                      String column,
                                      String entity,
                                      String message) throws Exception {
        String sql = "SELECT id FROM " + table + " WHERE " + column + " IS NULL OR TRIM(" + column + ") = ''";
        try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                issues.add(new SyncValidationIssue("ERROR", "MANDATORY_FIELD", entity, Long.toString(rs.getLong("id")), message));
            }
        }
    }

    private void collectInvalidAssignments(Connection connection, List<SyncValidationIssue> issues) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT d.id, d.assignment_id FROM distribution d LEFT JOIN assignments a ON a.id=d.assignment_id " +
                        "WHERE d.assignment_id IS NOT NULL AND a.id IS NULL"
        ); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                issues.add(new SyncValidationIssue("ERROR", "ORPHAN_ASSIGNMENT", "Distribution",
                        Long.toString(rs.getLong("id")), "Distribution references missing assignment " + rs.getLong("assignment_id") + "."));
            }
        }
    }

    private void collectInvalidReturns(Connection connection, List<SyncValidationIssue> issues) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT r.id, r.asset_code FROM returns r " +
                        "WHERE NOT EXISTS (" +
                        "    SELECT 1 FROM distribution d " +
                        "    WHERE LOWER(TRIM(d.asset_code)) = LOWER(TRIM(r.asset_code)) " +
                        "    LIMIT 1" +
                        ")"
        ); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                issues.add(new SyncValidationIssue("WARNING", "RETURN_WITHOUT_DISTRIBUTION", "Return",
                        rs.getString("asset_code"), "Return has no matching distribution. Verify legacy/imported data before syncing."));
            }
        }
    }

    private void collectInvalidContacts(Connection connection, List<SyncValidationIssue> issues) throws Exception {
        collectInvalidContactColumn(connection, issues, "distribution", "phone", "Distribution", "Malformed phone value");
        collectInvalidContactColumn(connection, issues, "distribution", "nid", "Distribution", "Malformed NID value");
        collectInvalidContactColumn(connection, issues, "returns", "phone", "Return", "Malformed phone value");
        collectInvalidContactColumn(connection, issues, "returns", "nid", "Return", "Malformed NID value");
    }

    private void collectInvalidContactColumn(Connection connection,
                                             List<SyncValidationIssue> issues,
                                             String table,
                                             String column,
                                             String entity,
                                             String message) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, " + column + " AS value FROM " + table + " WHERE " + column + " IS NOT NULL AND TRIM(" + column + ") <> ''"
        ); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String value = normalize(rs.getString("value"));
                String digits = value.replaceAll("[^0-9]", "");
                if (digits.length() < 6 || digits.length() > 20) {
                    issues.add(new SyncValidationIssue("WARNING", "CONTACT_FORMAT", entity,
                            Long.toString(rs.getLong("id")), message + ": " + value));
                }
            }
        }
    }

    private Set<String> normalizeEntityScope(Set<String> entityTypes) {
        Set<String> normalized = new LinkedHashSet<>();
        if (entityTypes == null) {
            return normalized;
        }
        for (String entityType : entityTypes) {
            String value = normalize(entityType).toUpperCase();
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private boolean isDestructiveStatus(SyncStorageRepository.StoredSyncQueueItem item) {
        String status = item.getPayload().path("status").asText();
        return "RETIRED".equalsIgnoreCase(status) || "FROZEN".equalsIgnoreCase(status) || "DELETED".equalsIgnoreCase(status);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private void applyQueueItem(SyncStorageRepository.StoredSyncQueueItem item) throws Exception {
        String entityType = normalize(item.getEntityType());
        String operationType = normalize(item.getOperationType());
        switch (entityType) {
            case "EQUIPMENT":
                applyEquipment(item, operationType);
                return;
            case "ASSIGNMENT":
                applyAssignment(item, operationType);
                return;
            case "DISTRIBUTION":
                applyDistribution(item);
                return;
            case "RETURN":
                applyReturn(item);
                return;
            case "USER":
                applyUser(item, operationType);
                return;
            case "DEPARTMENT":
                applyDepartment(item, operationType);
                return;
            default:
                throw new SyncConflictException("Unsupported queued entity type: " + entityType);
        }
    }

    private void applyDepartment(SyncStorageRepository.StoredSyncQueueItem item, String operationType) throws Exception {
        JsonNode payload = item.getPayload();
        switch (operationType) {
            case "CREATE":
                remoteMirrorCoordinator.getRemoteDepartmentService().createDepartment(payload.path("name").asText());
                return;
            case "UPDATE":
                remoteMirrorCoordinator.getRemoteDepartmentService().updateDepartment(
                        payload.path("oldName").asText(),
                        payload.path("name").asText()
                );
                return;
            case "DELETE":
                remoteMirrorCoordinator.getRemoteDepartmentService().deleteDepartment(payload.path("name").asText());
                return;
            default:
                throw new SyncConflictException("Unsupported department operation: " + operationType);
        }
    }

    private void applyEquipment(SyncStorageRepository.StoredSyncQueueItem item, String operationType) throws Exception {
        JsonNode payload = item.getPayload();
        JsonNode baseline = item.getBaselineSnapshot();
        switch (operationType) {
            case "CREATE":
                Equipment equipment = new Equipment(
                        payload.path("name").asText(),
                        payload.path("category").asText(),
                        payload.path("serialNumber").asText(),
                        payload.path("source").asText(),
                        payload.path("condition").asText(),
                        payload.path("entryDate").asText()
                );
                remoteMirrorCoordinator.getRemoteEquipmentService().createEquipment(equipment);
                return;
            case "UPDATE":
                Equipment remoteEquipment = resolveRemoteEquipment(payload, baseline);
                ensureEquipmentBaseline(remoteEquipment, baseline);
                remoteMirrorCoordinator.getRemoteEquipmentService().updateEquipment(
                        remoteEquipment.getAssetCode(),
                        payload.path("serialNumber").asText(),
                        payload.path("name").asText(),
                        payload.path("category").asText(),
                        payload.path("condition").asText()
                );
                return;
            case "STATUS":
                Equipment statusEquipment = resolveRemoteEquipment(payload, baseline);
                ensureEquipmentBaseline(statusEquipment, baseline);
                remoteMirrorCoordinator.getRemoteEquipmentService().updateEquipmentStatus(
                        statusEquipment.getAssetCode(),
                        payload.path("status").asText()
                );
                return;
            case "DELETE":
                Equipment deleteEquipment = resolveRemoteEquipment(payload, baseline);
                ensureEquipmentBaseline(deleteEquipment, baseline);
                remoteMirrorCoordinator.getRemoteEquipmentService().deleteEquipment(deleteEquipment.getAssetCode());
                return;
            default:
                throw new SyncConflictException("Unsupported equipment operation: " + operationType);
        }
    }

    private void applyAssignment(SyncStorageRepository.StoredSyncQueueItem item, String operationType) throws Exception {
        JsonNode payload = item.getPayload();
        JsonNode baseline = item.getBaselineSnapshot();
        switch (operationType) {
            case "CREATE":
                remoteMirrorCoordinator.getRemoteAssignmentService().createAssignment(
                        payload.path("person").asText(),
                        payload.path("department").asText(),
                        payload.path("equipmentType").asText(),
                        payload.path("reason").asText(),
                        payload.path("quantity").asInt()
                );
                return;
            case "UPDATE":
                Assignment remoteAssignment = resolveRemoteAssignment(payload, baseline);
                ensureAssignmentBaseline(remoteAssignment, baseline);
                remoteMirrorCoordinator.getRemoteAssignmentService().updateAssignment(
                        remoteAssignment.getId(),
                        payload.path("person").asText(),
                        payload.path("department").asText(),
                        payload.path("equipmentType").asText(),
                        payload.path("reason").asText(),
                        payload.path("quantity").asInt()
                );
                return;
            case "STATUS":
                Assignment statusAssignment = resolveRemoteAssignment(payload, baseline);
                ensureAssignmentBaseline(statusAssignment, baseline);
                remoteMirrorCoordinator.getRemoteAssignmentService().updateAssignmentStatus(
                        statusAssignment.getId(),
                        payload.path("status").asText()
                );
                return;
            case "DELETE":
                Assignment deleteAssignment = resolveRemoteAssignment(payload, baseline);
                ensureAssignmentBaseline(deleteAssignment, baseline);
                remoteMirrorCoordinator.getRemoteAssignmentService().deleteAssignment(deleteAssignment.getId());
                return;
            default:
                throw new SyncConflictException("Unsupported assignment operation: " + operationType);
        }
    }

    private void applyDistribution(SyncStorageRepository.StoredSyncQueueItem item) throws Exception {
        JsonNode payload = item.getPayload();
        Assignment assignment = resolveRemoteAssignment(payload.path("assignment"), null);
        if (assignment == null) {
            throw new SyncConflictException("The related assignment no longer exists in the central database.");
        }
        List<Distribution> distributions = new ArrayList<>();
        for (JsonNode node : payload.path("items")) {
            Equipment remoteEquipment = resolveRemoteEquipment(node, null);
            if (remoteEquipment == null) {
                throw new SyncConflictException("One of the distributed assets no longer exists in the central database.");
            }
            Distribution distribution = new Distribution(
                    remoteEquipment.getAssetCode(),
                    remoteEquipment.getSerialNumber(),
                    node.path("assignedTo").asText(),
                    node.path("phone").asText(),
                    node.path("nid").asText()
            );
            String date = node.path("date").asText("");
            if (!date.isBlank()) {
                distribution.setDistributionDate(java.time.LocalDate.parse(date));
            }
            distributions.add(distribution);
        }
        remoteMirrorCoordinator.getRemoteDistributionService().distributeEquipmentBatch(assignment.getId(), distributions);
    }

    private void applyReturn(SyncStorageRepository.StoredSyncQueueItem item) throws Exception {
        JsonNode payload = item.getPayload();
        Assignment assignment = resolveRemoteAssignment(payload.path("assignment"), null);
        if (assignment == null) {
            throw new SyncConflictException("The related assignment no longer exists in the central database.");
        }

        List<ReturnDraft> drafts = new ArrayList<>();
        for (JsonNode node : payload.path("items")) {
            Equipment remoteEquipment = resolveRemoteEquipment(node, null);
            String assetCode = remoteEquipment == null ? node.path("originalAssetCode").asText() : remoteEquipment.getAssetCode();
            drafts.add(new ReturnDraft(
                    assetCode,
                    node.path("enteredIdentifier").asText(),
                    node.path("returnedBy").asText(),
                    node.path("phone").asText(),
                    node.path("nid").asText(),
                    node.path("condition").asText(),
                    node.path("remarks").asText(),
                    node.path("replacement").asBoolean(false)
            ));
        }

        remoteMirrorCoordinator.getRemoteReturnService().saveReturns(
                assignment.getId(),
                payload.path("equipmentType").asText(),
                drafts,
                parseOutstandingRemarks(payload)
        );
    }

    private Map<String, String> parseOutstandingRemarks(JsonNode payload) {
        Map<String, String> remarks = new LinkedHashMap<>();
        JsonNode node = payload.path("outstandingRemarks");
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> remarks.put(entry.getKey(), entry.getValue().asText()));
        }
        if (remarks.isEmpty() && payload.has("outstandingRemark")) {
            String legacyRemark = payload.path("outstandingRemark").asText();
            for (JsonNode itemNode : payload.path("items")) {
                String assetCode = itemNode.path("originalAssetCode").asText();
                if (!assetCode.isBlank()) {
                    remarks.put(assetCode, legacyRemark);
                }
            }
        }
        return remarks;
    }

    private void applyUser(SyncStorageRepository.StoredSyncQueueItem item, String operationType) throws Exception {
        JsonNode payload = item.getPayload();
        JsonNode baseline = item.getBaselineSnapshot();
        switch (operationType) {
            case "CREATE":
                remoteMirrorCoordinator.getRemoteUserService().createUser(
                        payload.path("name").asText(),
                        payload.path("password").asText(),
                        payload.path("role").asText(),
                        payload.path("department").asText(),
                        payload.path("email").asText()
                );
                return;
            case "UPDATE":
                User remoteUser = resolveRemoteUser(payload, baseline);
                ensureUserBaseline(remoteUser, baseline);
                remoteMirrorCoordinator.getRemoteUserService().updateUser(
                        remoteUser.getId(),
                        payload.path("name").asText(),
                        payload.path("password").asText(),
                        payload.path("role").asText(),
                        payload.path("department").asText(),
                        payload.path("email").asText()
                );
                return;
            case "STATUS":
                User statusUser = resolveRemoteUser(payload, baseline);
                ensureUserBaseline(statusUser, baseline);
                remoteMirrorCoordinator.getRemoteUserService().updateUserStatus(
                        statusUser.getId(),
                        payload.path("status").asText()
                );
                return;
            case "DELETE":
                User deleteUser = resolveRemoteUser(payload, baseline);
                ensureUserBaseline(deleteUser, baseline);
                remoteMirrorCoordinator.getRemoteUserService().deleteUser(deleteUser.getId());
                return;
            default:
                throw new SyncConflictException("Unsupported user operation: " + operationType);
        }
    }

    private Equipment resolveRemoteEquipment(JsonNode primary, JsonNode fallback) {
        String serialNumber = firstNonBlank(primary.path("serialNumber").asText(), fallback == null ? "" : fallback.path("serialNumber").asText());
        String assetCode = firstNonBlank(primary.path("assetCode").asText(), fallback == null ? "" : fallback.path("assetCode").asText());
        for (Equipment equipment : remoteMirrorCoordinator.getRemoteReportService().getInventoryReport()) {
            if (!serialNumber.isBlank() && serialNumber.equalsIgnoreCase(normalize(equipment.getSerialNumber()))) {
                return equipment;
            }
            if (!assetCode.isBlank() && assetCode.equalsIgnoreCase(normalize(equipment.getAssetCode()))) {
                return equipment;
            }
        }
        return null;
    }

    private Assignment resolveRemoteAssignment(JsonNode primary, JsonNode fallback) throws SyncConflictException {
        JsonNode source = primary != null && !primary.isMissingNode() ? primary : fallback;
        if (source == null || source.isMissingNode()) {
            return null;
        }
        int remoteId = source.path("id").asInt(0);
        List<Assignment> assignments = remoteMirrorCoordinator.getRemoteAssignmentService().getAssignments();
        if (remoteId > 0) {
            for (Assignment assignment : assignments) {
                if (assignment.getId() == remoteId) {
                    return assignment;
                }
            }
            return null;
        }

        String person = normalize(source.path("person").asText());
        String department = normalize(source.path("department").asText());
        String equipmentType = normalize(source.path("equipmentType").asText());
        String reason = normalize(source.path("reason").asText());
        int quantity = source.path("quantity").asInt(0);
        List<Assignment> matches = new ArrayList<>();
        for (Assignment assignment : assignments) {
            if (person.equalsIgnoreCase(normalize(assignment.getPerson()))
                    && department.equalsIgnoreCase(normalize(assignment.getDepartment()))
                    && equipmentType.equalsIgnoreCase(normalize(assignment.getEquipmentType()))
                    && reason.equalsIgnoreCase(normalize(assignment.getReason()))
                    && quantity == assignment.getQuantity()) {
                matches.add(assignment);
            }
        }
        if (matches.size() > 1) {
            throw new SyncConflictException("Assignment sync is ambiguous because multiple central assignments match the queued record.");
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private User resolveRemoteUser(JsonNode primary, JsonNode fallback) {
        String email = firstNonBlank(primary.path("email").asText(), fallback == null ? "" : fallback.path("email").asText());
        for (User user : remoteMirrorCoordinator.getRemoteUserService().getUsers()) {
            if (!email.isBlank() && email.equalsIgnoreCase(normalize(user.getEmail()))) {
                return user;
            }
        }
        return null;
    }

    private void ensureEquipmentBaseline(Equipment remoteEquipment, JsonNode baseline) throws SyncConflictException {
        if (baseline == null || baseline.isMissingNode()) {
            return;
        }
        if (remoteEquipment == null) {
            throw new SyncConflictException("The equipment record no longer exists in the central database.");
        }
        if (!matches(normalize(remoteEquipment.getSerialNumber()), baseline.path("serialNumber").asText())
                || !matches(normalize(remoteEquipment.getName()), baseline.path("name").asText())
                || !matches(normalize(remoteEquipment.getCategory()), baseline.path("category").asText())
                || !matches(normalize(remoteEquipment.getCondition()), baseline.path("condition").asText())
                || !matches(normalize(remoteEquipment.getStatus()), baseline.path("status").asText())) {
            throw new SyncConflictException("The equipment record changed in the central database while this action was offline.");
        }
    }

    private void ensureAssignmentBaseline(Assignment remoteAssignment, JsonNode baseline) throws SyncConflictException {
        if (baseline == null || baseline.isMissingNode()) {
            return;
        }
        if (remoteAssignment == null) {
            throw new SyncConflictException("The assignment record no longer exists in the central database.");
        }
        if (!matches(normalize(remoteAssignment.getPerson()), baseline.path("person").asText())
                || !matches(normalize(remoteAssignment.getDepartment()), baseline.path("department").asText())
                || !matches(normalize(remoteAssignment.getEquipmentType()), baseline.path("equipmentType").asText())
                || !matches(normalize(remoteAssignment.getReason()), baseline.path("reason").asText())
                || remoteAssignment.getQuantity() != baseline.path("quantity").asInt()) {
            throw new SyncConflictException("The assignment record changed in the central database while this action was offline.");
        }
    }

    private void ensureUserBaseline(User remoteUser, JsonNode baseline) throws SyncConflictException {
        if (baseline == null || baseline.isMissingNode()) {
            return;
        }
        if (remoteUser == null) {
            throw new SyncConflictException("The user record no longer exists in the central database.");
        }
        if (!matches(normalize(remoteUser.getFullName()), baseline.path("name").asText())
                || !matches(normalize(remoteUser.getRole()), baseline.path("role").asText())
                || !matches(normalize(remoteUser.getDepartment()), baseline.path("department").asText())
                || !matches(normalize(remoteUser.getStatus()), baseline.path("status").asText())) {
            throw new SyncConflictException("The user record changed in the central database while this action was offline.");
        }
    }

    private boolean matches(String actual, String expected) {
        return normalize(actual).equalsIgnoreCase(normalize(expected));
    }

    private String currentActor() {
        User currentUser = Session.getCurrentUser();
        if (currentUser == null) {
            return "unknown";
        }
        if (currentUser.getEmail() != null && !currentUser.getEmail().isBlank()) {
            return currentUser.getEmail();
        }
        return currentUser.getUsername();
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = normalize(first);
        return normalizedFirst.isBlank() ? normalize(second) : normalizedFirst;
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "The sync operation failed."
                : exception.getMessage();
    }

    private boolean isBusinessRejection(ApiClientException exception) {
        int statusCode = exception.getStatusCode();
        return statusCode == 400
                || statusCode == 403
                || statusCode == 404
                || statusCode == 409;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
