package com.mycompany.msr.amis.api.dto.equipment;

import jakarta.validation.constraints.NotBlank;

public record EquipmentStatusUpdateRequest(
        @NotBlank String status
) {
}
