package com.mycompany.msr.amis.api.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserRequest(
        @NotBlank String fullName,
        @NotBlank String username,
        @Email @NotBlank String email,
        @NotBlank String role,
        @NotBlank String department,
        String phone,
        String password
) {
}
