package com.mycompany.msr.amis.api.controller;

import com.mycompany.msr.amis.api.dto.CommonMessageResponse;
import com.mycompany.msr.amis.api.dto.user.UserRequest;
import com.mycompany.msr.amis.api.dto.user.UserResponse;
import com.mycompany.msr.amis.api.dto.user.UserStatusUpdateRequest;
import com.mycompany.msr.amis.api.service.UserManagementService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserManagementService userManagementService;

    public UserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public List<UserResponse> listUsers(Authentication authentication) {
        return userManagementService.listVisibleUsers(authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public UserResponse createUser(Authentication authentication, @Valid @RequestBody UserRequest request) {
        return userManagementService.createUser(authentication.getName(), request);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public UserResponse updateUser(Authentication authentication,
                                   @PathVariable Long userId,
                                   @Valid @RequestBody UserRequest request) {
        return userManagementService.updateUser(authentication.getName(), userId, request);
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public UserResponse updateUserStatus(Authentication authentication,
                                         @PathVariable Long userId,
                                         @Valid @RequestBody UserStatusUpdateRequest request) {
        return userManagementService.updateStatus(authentication.getName(), userId, request.status());
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CommonMessageResponse deleteUser(Authentication authentication, @PathVariable Long userId) {
        return userManagementService.deleteUser(authentication.getName(), userId);
    }
}
