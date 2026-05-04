package com.mycompany.msr.amis;

public final class SyncCenterSummary {

    private final int pendingCount;
    private final int appliedCount;
    private final int rejectedCount;
    private final int failedCount;
    private final boolean onlineReady;
    private final String statusMessage;

    public SyncCenterSummary(int pendingCount,
                             int appliedCount,
                             int rejectedCount,
                             int failedCount,
                             boolean onlineReady,
                             String statusMessage) {
        this.pendingCount = pendingCount;
        this.appliedCount = appliedCount;
        this.rejectedCount = rejectedCount;
        this.failedCount = failedCount;
        this.onlineReady = onlineReady;
        this.statusMessage = statusMessage == null ? "" : statusMessage;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public int getAppliedCount() {
        return appliedCount;
    }

    public int getRejectedCount() {
        return rejectedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public boolean isOnlineReady() {
        return onlineReady;
    }

    public String getStatusMessage() {
        return statusMessage;
    }
}
