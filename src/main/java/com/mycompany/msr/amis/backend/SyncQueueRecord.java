package com.mycompany.msr.amis;

public final class SyncQueueRecord {

    private final long id;
    private final String entityType;
    private final String operationType;
    private final String entityKey;
    private final String actor;
    private final String status;
    private final int retryCount;
    private final String machineId;
    private final String idempotencyKey;
    private final String description;
    private final String errorMessage;
    private final String createdAt;
    private final String processedAt;

    public SyncQueueRecord(long id,
                           String entityType,
                           String operationType,
                           String entityKey,
                           String actor,
                           String status,
                           int retryCount,
                           String machineId,
                           String idempotencyKey,
                           String description,
                           String errorMessage,
                           String createdAt,
                           String processedAt) {
        this.id = id;
        this.entityType = safe(entityType);
        this.operationType = safe(operationType);
        this.entityKey = safe(entityKey);
        this.actor = safe(actor);
        this.status = safe(status);
        this.retryCount = retryCount;
        this.machineId = safe(machineId);
        this.idempotencyKey = safe(idempotencyKey);
        this.description = safe(description);
        this.errorMessage = safe(errorMessage);
        this.createdAt = safe(createdAt);
        this.processedAt = safe(processedAt);
    }

    public long getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getOperationType() {
        return operationType;
    }

    public String getEntityKey() {
        return entityKey;
    }

    public String getActor() {
        return actor;
    }

    public String getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getMachineId() {
        return machineId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getDescription() {
        return description;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getProcessedAt() {
        return processedAt;
    }

    public String getLocalVersionTimestamp() {
        return createdAt;
    }

    public String getCentralVersionTimestamp() {
        return processedAt;
    }

    public String getConflictType() {
        String message = (errorMessage == null ? "" : errorMessage).toLowerCase();
        if (message.contains("changed in the central database")) {
            return "CENTRAL_RECORD_CHANGED";
        }
        if (message.contains("no longer exists")) {
            return "CENTRAL_RECORD_MISSING";
        }
        if (message.contains("already exists")) {
            return "DUPLICATE_CENTRAL_RECORD";
        }
        if ("REJECTED".equalsIgnoreCase(status)) {
            return "BUSINESS_RULE_REJECTION";
        }
        if ("FAILED".equalsIgnoreCase(status)) {
            return "SYNC_FAILURE";
        }
        return "";
    }

    public String getResolutionAction() {
        if ("REJECTED".equalsIgnoreCase(status)) {
            return "Review required";
        }
        if ("FAILED".equalsIgnoreCase(status)) {
            return "Retry available";
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
