package com.mycompany.msr.amis.api.dto.sync;

import java.util.List;

public record SyncPushRequest(
        String sessionToken,
        String machineId,
        String machineName,
        boolean dryRun,
        List<String> entities,
        List<SyncQueueRecordRequest> records
) {
}
