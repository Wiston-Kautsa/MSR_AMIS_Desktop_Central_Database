package com.mycompany.msr.amis.api.dto.assignment;

import jakarta.validation.constraints.NotBlank;

public record AssignmentStatusUpdateRequest(
        @NotBlank String status
) {
}
