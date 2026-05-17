package com.mycompany.msr.amis;

import java.util.List;
import java.util.Set;

public interface SyncCenterService {

    SyncCenterSummary getSummary() throws Exception;

    List<SyncQueueRecord> getQueueRecords() throws Exception;

    List<SyncAuditRecord> getAuditRecords() throws Exception;

    long queueOperation(String entityType,
                        String operationType,
                        String entityKey,
                        Object payload,
                        Object baselineSnapshot,
                        String description) throws Exception;

    String processPendingQueue() throws Exception;

    String processPendingQueueForActor(String actor) throws Exception;

    String pushPendingEquipment() throws Exception;

    String pushPendingEquipmentForActor(String actor) throws Exception;

    String retryRejectedQueue() throws Exception;

    String pullFromCentralServer() throws Exception;

    String clearCompletedLogs(String actor) throws Exception;

    String clearQueue(String actor) throws Exception;

    String resetSyncState(String actor) throws Exception;

    List<SyncValidationIssue> validateBeforeSync() throws Exception;

    String runDryRun(Set<String> entityTypes) throws Exception;

    SyncLockInfo getSyncLockInfo() throws Exception;

    String forceReleaseSyncLock(String actor) throws Exception;
}
