package com.mycompany.msr.amis.api.dto.assignment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AssignmentRequest(
        @NotBlank String person,
        @NotBlank String department,
        @NotBlank String equipmentType,
        @NotBlank String reason,
        @Min(1) int quantity
) {
}
