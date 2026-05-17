package com.mycompany.msr.amis.api.dto.sync;

import java.util.List;

public record SyncRetryRequest(
        List<Long> queueIds
) {
}
