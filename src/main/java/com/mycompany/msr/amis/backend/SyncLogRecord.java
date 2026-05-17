package com.mycompany.msr.amis;

public final class SyncLogRecord {

    private final String timestamp;
    private final String user;
    private final String machineName;
    private final String entity;
    private final String operation;
    private final int recordsCount;
    private final String duration;
    private final String result;
    private final String errorMessage;

    public SyncLogRecord(String timestamp,
                         String user,
                         String machineName,
                         String entity,
                         String operation,
                         int recordsCount,
                         String duration,
                         String result,
                         String errorMessage) {
        this.timestamp = safe(timestamp);
        this.user = safe(user);
        this.machineName = safe(machineName);
        this.entity = safe(entity);
        this.operation = safe(operation);
        this.recordsCount = recordsCount;
        this.duration = safe(duration);
        this.result = safe(result);
        this.errorMessage = safe(errorMessage);
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getUser() {
        return user;
    }

    public String getMachineName() {
        return machineName;
    }

    public String getEntity() {
        return entity;
    }

    public String getOperation() {
        return operation;
    }

    public int getRecordsCount() {
        return recordsCount;
    }

    public String getDuration() {
        return duration;
    }

    public String getResult() {
        return result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
