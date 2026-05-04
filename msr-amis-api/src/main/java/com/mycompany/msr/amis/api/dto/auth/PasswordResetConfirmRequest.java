package com.mycompany.msr.amis.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetConfirmRequest(
        @NotBlank String identifier,
        @NotBlank String resetCode,
        @NotBlank String newPassword
) {
}
