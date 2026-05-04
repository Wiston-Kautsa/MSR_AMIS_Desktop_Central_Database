package com.mycompany.msr.amis;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                ? "Central session is available. Pending offline actions can be replayed."
                : "Central session is not available. New offline actions will stay queued.";
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
        if (!remoteMirrorCoordinator.hasRemoteSession()) {
            throw new IllegalStateException("A live central session is required before pending offline actions can be synced.");
        }

        int applied = 0;
        int rejected = 0;

        try (Connection connection = DatabaseHandler.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (SyncStorageRepository.StoredSyncQueueItem item : storageRepository.loadQueueItems(connection)) {
                    String status = normalize(item.getStatus());
                    if (!"PENDING".equals(status) && !"FAILED".equals(status)) {
                        continue;
                    }

                    try {
                        applyQueueItem(item);
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
                        connection.commit();
                        throw exception;
                    }

                    connection.commit();
                    connection.setAutoCommit(false);
                    remoteMirrorCoordinator.synchronizeFromRemote(Map.of());
                }
            } finally {
                connection.setAutoCommit(true);
            }
        }

        return "Sync finished. Applied: " + applied + ". Rejected: " + rejected + ".";
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
            default:
                throw new SyncConflictException("Unsupported queued entity type: " + entityType);
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
                payload.path("outstandingRemark").asText()
        );
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

    private Assignment resolveRemoteAssignment(JsonNode primary, JsonNode fallback) {
        JsonNode source = primary != null && !primary.isMissingNode() ? primary : fallback;
        if (source == null || source.isMissingNode()) {
            return null;
        }
        int remoteId = source.path("id").asInt(0);
        String person = normalize(source.path("person").asText());
        String department = normalize(source.path("department").asText());
        String equipmentType = normalize(source.path("equipmentType").asText());
        String reason = normalize(source.path("reason").asText());
        int quantity = source.path("quantity").asInt(0);
        for (Assignment assignment : remoteMirrorCoordinator.getRemoteAssignmentService().getAssignments()) {
            if (remoteId > 0 && assignment.getId() == remoteId) {
                return assignment;
            }
            if (person.equalsIgnoreCase(normalize(assignment.getPerson()))
                    && department.equalsIgnoreCase(normalize(assignment.getDepartment()))
                    && equipmentType.equalsIgnoreCase(normalize(assignment.getEquipmentType()))
                    && reason.equalsIgnoreCase(normalize(assignment.getReason()))
                    && quantity == assignment.getQuantity()) {
                return assignment;
            }
        }
        return null;
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

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
