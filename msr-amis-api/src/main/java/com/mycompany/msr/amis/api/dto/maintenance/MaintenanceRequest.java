package com.mycompany.msr.amis.api.dto.maintenance;

import jakarta.validation.constraints.NotBlank;

public record MaintenanceRequest(
        @NotBlank String assetCode,
        @NotBlank String issue,
        String actionTaken,
        String performedBy,
        String maintenanceDate,
        String cost,
        boolean completed
) {
}
