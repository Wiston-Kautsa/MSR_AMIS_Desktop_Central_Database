package com.mycompany.msr.amis.api.service;

import org.springframework.stereotype.Repository;

@Repository
public class SyncIdempotencyRepository {

    private final SyncQueueService syncQueueService;

    public SyncIdempotencyRepository(SyncQueueService syncQueueService) {
        this.syncQueueService = syncQueueService;
    }

    public boolean alreadyProcessed(String idempotencyKey) {
        return syncQueueService.alreadyProcessed(idempotencyKey);
    }

    public void markProcessed(String actor,
                              String machineId,
                              String idempotencyKey,
                              String entityType,
                              String entityId,
                              String operation,
                              Object payload) {
        syncQueueService.markIdempotencyKeyProcessed(
                actor,
                machineId,
                idempotencyKey,
                entityType,
                entityId,
                operation,
                payload
        );
    }
}
