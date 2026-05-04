package com.mycompany.msr.amis.api.dto.assignment;

public record AssignmentResponse(
        int id,
        String person,
        String department,
        String equipmentType,
        String reason,
        int quantity,
        String date,
        int distributedCount,
        String status
) {
}
