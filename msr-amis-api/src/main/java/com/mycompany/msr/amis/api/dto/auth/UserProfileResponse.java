package com.mycompany.msr.amis.api.dto.auth;

public record UserProfileResponse(
        Long id,
        String fullName,
        String username,
        String role,
        String department,
        String email,
        String status,
        boolean mustChangePassword
) {
}
