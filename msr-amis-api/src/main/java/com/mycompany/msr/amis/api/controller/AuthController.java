package com.mycompany.msr.amis.api.controller;

import com.mycompany.msr.amis.api.dto.CommonMessageResponse;
import com.mycompany.msr.amis.api.dto.auth.InitialPasswordChangeRequest;
import com.mycompany.msr.amis.api.dto.auth.LoginRequest;
import com.mycompany.msr.amis.api.dto.auth.LoginResponse;
import com.mycompany.msr.amis.api.dto.auth.PasswordResetConfirmRequest;
import com.mycompany.msr.amis.api.dto.auth.PasswordResetRequest;
import com.mycompany.msr.amis.api.dto.auth.UserProfileResponse;
import com.mycompany.msr.amis.api.service.AuthFacadeService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthFacadeService authFacadeService;

    public AuthController(AuthFacadeService authFacadeService) {
        this.authFacadeService = authFacadeService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authFacadeService.login(request);
    }

    @GetMapping("/me")
    public UserProfileResponse me(Authentication authentication) {
        return authFacadeService.currentUser(authentication.getName());
    }

    @PostMapping("/password-reset/request")
    public CommonMessageResponse requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        return authFacadeService.requestPasswordReset(request);
    }

    @PostMapping("/password-reset/confirm")
    public CommonMessageResponse confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        return authFacadeService.confirmPasswordReset(request);
    }

    @PostMapping("/initial-password/change")
    public CommonMessageResponse changeInitialPassword(Authentication authentication,
                                                       @Valid @RequestBody InitialPasswordChangeRequest request) {
        return authFacadeService.changeInitialPassword(authentication.getName(), request);
    }

    @PostMapping("/bootstrap-admin/complete")
    public CommonMessageResponse completeBootstrapAdmin(Authentication authentication) {
        return authFacadeService.completeBootstrapAdmin(authentication.getName());
    }
}
