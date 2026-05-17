package com.mycompany.msr.amis.api.dto.sync;

import java.util.List;

public record SyncPushResponse(
        String sessionToken,
        String status,
        int total,
        int applied,
        int failed,
        int conflicts,
        int quarantined,
        String message,
        List<SyncPushItemResponse> results
) {
}
