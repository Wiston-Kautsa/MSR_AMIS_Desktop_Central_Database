package com.mycompany.msr.amis.api.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InitialAdminSetupRequest(
        @NotBlank String fullName,
        @NotBlank String username,
        @Email @NotBlank String email,
        String department,
        @NotBlank @Size(min = 8) String password
) {
}
