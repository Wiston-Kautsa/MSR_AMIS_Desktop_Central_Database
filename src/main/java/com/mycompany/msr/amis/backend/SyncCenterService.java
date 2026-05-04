package com.mycompany.msr.amis;

import java.util.List;

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
}
