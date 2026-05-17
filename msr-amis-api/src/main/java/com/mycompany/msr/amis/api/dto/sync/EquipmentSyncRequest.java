package com.mycompany.msr.amis.api.dto.sync;

import java.time.LocalDate;

public record EquipmentSyncRequest(
        String idempotencyKey,
        String assetCode,
        String name,
        String category,
        String serialNumber,
        String condition,
        String source,
        LocalDate entryDate,
        String status,
        String purchaseCost,
        String location,
        LocalDate warrantyExpiry,
        String supplier
) {
}
