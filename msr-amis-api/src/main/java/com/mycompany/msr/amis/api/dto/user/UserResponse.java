package com.mycompany.msr.amis.api.dto.user;

public record UserResponse(
        Long id,
        String fullName,
        String username,
        String role,
        String department,
        String phone,
        String email,
        String status
) {
}
