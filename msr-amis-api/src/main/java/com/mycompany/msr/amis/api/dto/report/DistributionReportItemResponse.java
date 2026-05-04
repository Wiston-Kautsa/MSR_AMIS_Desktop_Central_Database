package com.mycompany.msr.amis.api.dto.report;

public record DistributionReportItemResponse(
        int id,
        String assetCode,
        String responsiblePerson,
        String assignedTo,
        String phone,
        String nid,
        int assignmentId,
        String date,
        String status,
        String outstandingRemarks
) {
}
