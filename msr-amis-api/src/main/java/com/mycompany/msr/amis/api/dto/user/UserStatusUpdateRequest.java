package com.mycompany.msr.amis.api.dto.user;

import jakarta.validation.constraints.NotBlank;

public record UserStatusUpdateRequest(
        @NotBlank String status
) {
}
