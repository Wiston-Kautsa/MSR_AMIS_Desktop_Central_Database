package com.mycompany.msr.amis;

public final class SyncLockInfo {

    private final boolean active;
    private final String lockedBy;
    private final String startedAt;
    private final String sessionId;

    public SyncLockInfo(boolean active, String lockedBy, String startedAt, String sessionId) {
        this.active = active;
        this.lockedBy = safe(lockedBy);
        this.startedAt = safe(startedAt);
        this.sessionId = safe(sessionId);
    }

    public boolean isActive() {
        return active;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
