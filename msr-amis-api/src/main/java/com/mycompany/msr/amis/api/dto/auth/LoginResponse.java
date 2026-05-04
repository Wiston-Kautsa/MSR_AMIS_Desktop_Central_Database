package com.mycompany.msr.amis.api.dto.auth;

public record LoginResponse(
        String token,
        UserProfileResponse user
) {
}
