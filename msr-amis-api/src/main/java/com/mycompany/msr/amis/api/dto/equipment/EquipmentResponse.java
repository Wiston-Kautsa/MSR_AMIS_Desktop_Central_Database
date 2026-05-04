package com.mycompany.msr.amis.api.dto.equipment;

import java.time.LocalDate;

public record EquipmentResponse(
        Long id,
        String assetCode,
        String name,
        String category,
        String serialNumber,
        String condition,
        String source,
        LocalDate entryDate,
        String status
) {
}
