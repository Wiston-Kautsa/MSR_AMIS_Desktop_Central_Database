package com.mycompany.msr.amis;

public final class SyncAuditRecord {

    private final long id;
    private final long queueId;
    private final String actor;
    private final String action;
    private final String outcome;
    private final String details;
    private final String createdAt;

    public SyncAuditRecord(long id, long queueId, String actor, String action, String outcome, String details, String createdAt) {
        this.id = id;
        this.queueId = queueId;
        this.actor = safe(actor);
        this.action = safe(action);
        this.outcome = safe(outcome);
        this.details = safe(details);
        this.createdAt = safe(createdAt);
    }

    public long getId() {
        return id;
    }

    public long getQueueId() {
        return queueId;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getDetails() {
        return details;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
