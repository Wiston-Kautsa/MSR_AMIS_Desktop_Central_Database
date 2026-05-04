package com.mycompany.msr.amis.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record InitialPasswordChangeRequest(
        @NotBlank String newPassword
) {
}
