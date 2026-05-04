package com.mycompany.msr.amis.api.dto.asset;

public record AssetHistorySummaryResponse(
        String assetCode,
        String serialNumber,
        String equipmentName,
        String category,
        String entryDate,
        String currentStatus
) {
}
