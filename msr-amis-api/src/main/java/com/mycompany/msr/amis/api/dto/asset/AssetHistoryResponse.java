package com.mycompany.msr.amis.api.dto.asset;

import java.util.List;

public record AssetHistoryResponse(
        AssetHistorySummaryResponse summary,
        List<AssetHistoryRecordResponse> records
) {
}
