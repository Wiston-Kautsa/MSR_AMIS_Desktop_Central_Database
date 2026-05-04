package com.mycompany.msr.amis.api.dto.report;

public record ReturnReportItemResponse(
        String assetCode,
        String serialNumber,
        String equipmentName,
        String category,
        String source,
        String dateTaken,
        String responsibleOfficer,
        String assignmentEquipmentType,
        String assignmentReason,
        String returnedBy,
        String phone,
        String nid,
        String returnCondition,
        String remarks,
        String returnDate
) {
}
