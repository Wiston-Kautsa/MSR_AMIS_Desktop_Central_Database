package com.mycompany.msr.amis.api.dto.maintenance;

public record MaintenanceResponse(
        long id,
        String assetCode,
        String issue,
        String actionTaken,
        String performedBy,
        String maintenanceDate,
        String cost,
        String status
) {
}
