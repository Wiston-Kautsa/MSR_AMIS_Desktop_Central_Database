package com.mycompany.msr.amis.api.dto.report;

public record InventoryReportItemResponse(
        int id,
        String assetCode,
        String name,
        String category,
        String serialNumber,
        String condition,
        String source,
        String entryDate,
        String status,
        String purchaseCost,
        String location,
        String warrantyExpiry,
        String supplier
) {
}
