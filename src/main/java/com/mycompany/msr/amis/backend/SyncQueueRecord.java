package com.mycompany.msr.amis;

public final class SyncQueueRecord {

    private final long id;
    private final String entityType;
    private final String operationType;
    private final String entityKey;
    private final String actor;
    private final String status;
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

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
