package com.mycompany.msr.amis;

public final class SyncValidationIssue {

    private final String severity;
    private final String category;
    private final String entity;
    private final String recordIdentifier;
    private final String message;

    public SyncValidationIssue(String severity, String category, String entity, String recordIdentifier, String message) {
        this.severity = safe(severity);
        this.category = safe(category);
        this.entity = safe(entity);
        this.recordIdentifier = safe(recordIdentifier);
        this.message = safe(message);
    }

    public String getSeverity() {
        return severity;
    }

    public String getCategory() {
        return category;
    }

    public String getEntity() {
        return entity;
    }

    public String getRecordIdentifier() {
        return recordIdentifier;
    }

    public String getMessage() {
        return message;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
