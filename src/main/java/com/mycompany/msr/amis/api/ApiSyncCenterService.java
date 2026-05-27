package com.mycompany.msr.amis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.InetAddress;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ApiSyncCenterService implements SyncCenterService {

    private final ApiClient apiClient;
    private final SyncStorageRepository storageRepository = new SyncStorageRepository();

    public ApiSyncCenterService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public SyncCenterSummary getSummary() throws Exception {
        List<SyncQueueRecord> localRecords = getLocalQueueRecords();
        if (!localRecords.isEmpty()) {
            return localQueueSummary(localRecords);
        }

        JsonNode status = apiClient.getJson("/api/sync/status");
        return new SyncCenterSummary(
                status.path("pendingCount").asInt(),
                0,
                (int) status.path("conflictCount").asLong(),
                (int) status.path("failedCount").asLong(),
                "UP".equalsIgnoreCase(status.path("apiStatus").asText()),
                "Central sync API status: " + status.path("apiStatus").asText("UNKNOWN")
        );
    }

    @Override
    public List<SyncQueueRecord> getQueueRecords() throws Exception {
        List<SyncQueueRecord> localRecords = getLocalQueueRecords();
        if (!localRecords.isEmpty()) {
            return localRecords;
        }

        JsonNode queue = apiClient.getJson("/api/sync/queue");
        List<SyncQueueRecord> records = new ArrayList<>();
        if (queue != null && queue.isArray()) {
            for (JsonNode node : queue) {
                records.add(new SyncQueueRecord(
                        node.path("id").asLong(),
                        node.path("entityType").asText(),
                        node.path("operation").asText(),
                        node.path("entityId").asText(),
                        node.path("createdBy").asText(),
                        node.path("status").asText(),
                        node.path("retryCount").asInt(),
                        node.path("machineId").asText(),
                        node.path("idempotencyKey").asText(),
                        node.path("description").asText(),
                        node.path("errorMessage").asText(),
                        node.path("createdAt").asText(),
                        node.path("processedAt").asText()
                ));
            }
        }
        return records;
    }

    @Override
    public List<SyncAuditRecord> getAuditRecords() throws Exception {
        List<SyncAuditRecord> records = getLocalAuditRecords();
        JsonNode logs;
        try {
            logs = apiClient.getJson("/api/sync/audit");
        } catch (Exception exception) {
            if (!records.isEmpty()) {
                return records;
            }
            throw exception;
        }
        if (logs != null && logs.isArray()) {
            for (JsonNode node : logs) {
                records.add(new SyncAuditRecord(
                        node.path("id").asLong(),
                        0,
                        node.path("userId").asText(),
                        node.path("action").asText(),
                        node.path("status").asText(),
                        node.path("errorMessage").asText(),
                        node.path("createdAt").asText()
                ));
            }
        }
        return records;
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
        return pushLocalQueue(null, false);
    }

    @Override
    public String processPendingQueueForActor(String actor) throws Exception {
        return pushLocalQueue(actor, false);
    }

    @Override
    public String pushPendingEquipment() throws Exception {
        return pushLocalQueue(null, false);
    }

    @Override
    public String pushPendingEquipmentForActor(String actor) throws Exception {
        return pushLocalQueue(actor, false);
    }

    @Override
    public String retryRejectedQueue() throws Exception {
        JsonNode response = apiClient.post("/api/sync/retry", new EmptyPayload(), JsonNode.class);
        return response.path("message").asText("Retry request completed.");
    }

    @Override
    public String pullFromCentralServer() throws Exception {
        JsonNode response = apiClient.getJson("/api/sync/pull");
        return response.path("message").asText("Pull request completed.");
    }

    @Override
    public String clearCompletedLogs(String actor) throws Exception {
        JsonNode response = apiClient.post("/api/sync/queue/clear-completed", new EmptyPayload(), JsonNode.class);
        return response.path("message").asText("Completed sync logs cleared.");
    }

    @Override
    public String clearQueue(String actor) throws Exception {
        JsonNode response = apiClient.post("/api/sync/queue/clear", new EmptyPayload(), JsonNode.class);
        return response.path("message").asText("Central sync queue cleared.");
    }

    @Override
    public String resetSyncState(String actor) throws Exception {
        JsonNode response = apiClient.post("/api/sync/reset", new EmptyPayload(), JsonNode.class);
        return response.path("message").asText("Central sync state reset.");
    }

    @Override
    public List<SyncValidationIssue> validateBeforeSync() {
        return List.of();
    }

    @Override
    public String runDryRun(Set<String> entityTypes) throws Exception {
        JsonNode response = postLocalQueue(null, true).response();
        return response.path("message").asText("Dry run completed.");
    }

    @Override
    public SyncLockInfo getSyncLockInfo() throws Exception {
        JsonNode status = apiClient.getJson("/api/sync/status");
        return new SyncLockInfo(
                status.path("lockActive").asBoolean(false),
                status.path("lockedBy").asText(),
                "",
                ""
        );
    }

    @Override
    public String forceReleaseSyncLock(String actor) throws Exception {
        JsonNode response = apiClient.post("/api/sync/lock/force-release", new EmptyPayload(), JsonNode.class);
        return response.path("message").asText("Central sync lock released.");
    }

    private String pushLocalQueue(String actorScope, boolean dryRun) throws Exception {
        PushResult pushResult = postLocalQueue(actorScope, dryRun);
        if (dryRun) {
            return pushResult.response().path("message").asText("Dry run completed.");
        }

        int applied = 0;
        int failed = 0;
        JsonNode results = pushResult.response().path("results");
        try (Connection connection = DatabaseHandler.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (int i = 0; i < pushResult.items().size(); i++) {
                    SyncStorageRepository.StoredSyncQueueItem item = pushResult.items().get(i);
                    JsonNode result = results.isArray() && i < results.size() ? results.get(i) : null;
                    String status = result == null ? "FAILED" : result.path("status").asText("FAILED");
                    String message = result == null ? "No API result returned for queue item." : result.path("message").asText("");
                    if ("APPLIED".equalsIgnoreCase(status)) {
                        storageRepository.markApplied(connection, item.getId(), message);
                        storageRepository.insertAuditRecord(connection, item.getId(), item.getActor(), "SYNC_PUSH_APPLIED", "APPLIED", message);
                        applied++;
                    } else {
                        storageRepository.markFailed(connection, item.getId(), message);
                        storageRepository.insertAuditRecord(connection, item.getId(), item.getActor(), "SYNC_PUSH_FAILED", "FAILED", message);
                        failed++;
                    }
                }
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        return "Sync push completed. Applied: " + applied + ". Failed: " + failed + ".";
    }

    private PushResult postLocalQueue(String actorScope, boolean dryRun) throws Exception {
        List<SyncStorageRepository.StoredSyncQueueItem> items = pendingLocalSyncItems(actorScope);
        if (items.isEmpty()) {
            return new PushResult(apiMessage("No pending local queue records to push."), items);
        }

        List<SyncPushRecordPayload> records = new ArrayList<>();
        for (SyncStorageRepository.StoredSyncQueueItem item : items) {
            records.add(new SyncPushRecordPayload(
                    item.getEntityType(),
                    item.getEntityKey(),
                    item.getOperationType(),
                    item.getIdempotencyKey(),
                    "",
                    item.getPayload(),
                    item.getBaselineSnapshot()
            ));
        }

        JsonNode response = apiClient.post(
                "/api/sync/push",
                new SyncPushPayload("SYNC-" + UUID.randomUUID(), machineId(), machineId(), dryRun, List.of(), records),
                JsonNode.class
        );
        return new PushResult(response, items);
    }

    private List<SyncStorageRepository.StoredSyncQueueItem> pendingLocalSyncItems(String actorScope) throws Exception {
        List<SyncStorageRepository.StoredSyncQueueItem> items = new ArrayList<>();
        try (Connection connection = DatabaseHandler.getConnection()) {
            for (SyncStorageRepository.StoredSyncQueueItem item : storageRepository.loadQueueItems(connection)) {
                String status = normalize(item.getStatus());
                if (!"PENDING".equalsIgnoreCase(status) && !"FAILED".equalsIgnoreCase(status)) {
                    continue;
                }
                if (actorScope != null && !actorScope.isBlank()
                        && !actorScope.trim().equalsIgnoreCase(normalize(item.getActor()))) {
                    continue;
                }
                items.add(item);
            }
        }
        return items;
    }

    private List<SyncQueueRecord> getLocalQueueRecords() throws Exception {
        List<SyncQueueRecord> records = new ArrayList<>();
        try (Connection connection = DatabaseHandler.getConnection()) {
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
        } catch (Exception ignored) {
            return List.of();
        }
        return records;
    }

    private List<SyncAuditRecord> getLocalAuditRecords() throws Exception {
        try (Connection connection = DatabaseHandler.getConnection()) {
            return new ArrayList<>(storageRepository.loadAuditRecords(connection));
        }
    }

    private SyncCenterSummary localQueueSummary(List<SyncQueueRecord> records) {
        int pending = 0;
        int applied = 0;
        int rejected = 0;
        int failed = 0;
        for (SyncQueueRecord record : records) {
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
        boolean onlineReady = false;
        try {
            JsonNode status = apiClient.getJson("/api/sync/status");
            onlineReady = "UP".equalsIgnoreCase(status.path("apiStatus").asText());
        } catch (Exception ignored) {
            // The local queue is still usable while the central API is unavailable.
        }
        String message = onlineReady
                ? "Central sync API is available. Local queued records are ready to push."
                : "Central sync API is not reachable. Local queued records will stay pending.";
        return new SyncCenterSummary(pending, applied, rejected, failed, onlineReady, message);
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

    private JsonNode apiMessage(String message) throws Exception {
        ObjectNode node = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .build()
                .createObjectNode();
        node.put("message", message);
        node.putArray("results");
        return node;
    }

    private String machineId() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            if (host != null && !host.isBlank()) {
                return host.trim();
            }
        } catch (Exception ignored) {
            // Fall through to environment fallback.
        }
        String computerName = System.getenv("COMPUTERNAME");
        return computerName == null || computerName.isBlank() ? "UNKNOWN_MACHINE" : computerName.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class SyncPushPayload {
        public final String sessionToken;
        public final String machineId;
        public final String machineName;
        public final boolean dryRun;
        public final List<String> entities;
        public final List<SyncPushRecordPayload> records;

        private SyncPushPayload(String sessionToken,
                                String machineId,
                                String machineName,
                                boolean dryRun,
                                List<String> entities,
                                List<SyncPushRecordPayload> records) {
            this.sessionToken = sessionToken;
            this.machineId = machineId;
            this.machineName = machineName;
            this.dryRun = dryRun;
            this.entities = entities;
            this.records = records;
        }
    }

    private static final class SyncPushRecordPayload {
        public final String entityType;
        public final String entityId;
        public final String operation;
        public final String idempotencyKey;
        public final String checksum;
        public final JsonNode payload;
        public final JsonNode baseline;

        private SyncPushRecordPayload(String entityType,
                                      String entityId,
                                      String operation,
                                      String idempotencyKey,
                                      String checksum,
                                      JsonNode payload,
                                      JsonNode baseline) {
            this.entityType = entityType;
            this.entityId = entityId;
            this.operation = operation;
            this.idempotencyKey = idempotencyKey;
            this.checksum = checksum;
            this.payload = payload;
            this.baseline = baseline;
        }
    }

    private static final class PushResult {
        private final JsonNode response;
        private final List<SyncStorageRepository.StoredSyncQueueItem> items;

        private PushResult(JsonNode response, List<SyncStorageRepository.StoredSyncQueueItem> items) {
            this.response = response;
            this.items = items;
        }

        private JsonNode response() {
            return response;
        }

        private List<SyncStorageRepository.StoredSyncQueueItem> items() {
            return items;
        }
    }

    private static final class EmptyPayload {
    }
}
