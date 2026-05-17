package com.mycompany.msr.amis.api.dto.equipment;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record EquipmentRequest(
        @NotBlank String name,
        @NotBlank String category,
        @NotBlank String serialNumber,
        String source,
        String condition,
        LocalDate entryDate,
        String purchaseCost,
        String location,
        LocalDate warrantyExpiry,
        String supplier
) {
}
