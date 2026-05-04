package com.mycompany.msr.amis.api.service;

import com.mycompany.msr.amis.api.domain.UserAccount;
import com.mycompany.msr.amis.api.domain.UserRole;
import com.mycompany.msr.amis.api.domain.UserStatus;
import com.mycompany.msr.amis.api.dto.CommonMessageResponse;
import com.mycompany.msr.amis.api.dto.auth.InitialPasswordChangeRequest;
import com.mycompany.msr.amis.api.dto.auth.LoginRequest;
import com.mycompany.msr.amis.api.dto.auth.LoginResponse;
import com.mycompany.msr.amis.api.dto.auth.PasswordResetConfirmRequest;
import com.mycompany.msr.amis.api.dto.auth.PasswordResetRequest;
import com.mycompany.msr.amis.api.dto.auth.UserProfileResponse;
import com.mycompany.msr.amis.api.exception.ApiException;
import com.mycompany.msr.amis.api.repository.UserRepository;
import com.mycompany.msr.amis.api.security.JwtService;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthFacadeService {

    private static final String PRIMARY_SUPER_ADMIN_EMAIL = "wkautsa@gmail.com";
    private static final String DEFAULT_ADMIN_EMAIL = "admin@msr.local";
    private static final String DEFAULT_USER_EMAIL = "user@msr.local";
    private static final Set<String> BOOTSTRAP_EMAILS = Set.of(DEFAULT_ADMIN_EMAIL, DEFAULT_USER_EMAIL);

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetEmailService passwordResetEmailService;
    private final JdbcTemplate jdbcTemplate;
    private final boolean exposeResetCodeOnEmailFailure;

    public AuthFacadeService(UserRepository userRepository,
                             JwtService jwtService,
                             PasswordEncoder passwordEncoder,
                             PasswordResetEmailService passwordResetEmailService,
                             JdbcTemplate jdbcTemplate,
                             @Value("${app.security.password-reset.expose-code-when-email-disabled:true}")
                             boolean exposeResetCodeOnEmailFailure) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetEmailService = passwordResetEmailService;
        this.jdbcTemplate = jdbcTemplate;
        this.exposeResetCodeOnEmailFailure = exposeResetCodeOnEmailFailure;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserAccount account = userRepository
                .findByEmailIgnoreCaseOrUsernameIgnoreCase(request.identifier(), request.identifier())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));

        if (isPrimarySuperAdmin(account) && account.isMustChangePassword()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "The primary super admin must reset the password before signing in."
            );
        }

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }

        if (account.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This account is not active.");
        }

        account.setLastLoginAt(OffsetDateTime.now());

        org.springframework.security.core.userdetails.UserDetails principal =
                org.springframework.security.core.userdetails.User.withUsername(account.getEmail())
                        .password(account.getPasswordHash())
                        .authorities("ROLE_" + account.getRole().name())
                        .accountLocked(false)
                        .disabled(false)
                        .build();

        String token = jwtService.generateToken(
                Map.of("role", account.getRole().name(), "email", account.getEmail()),
                principal
        );

        return new LoginResponse(token, toProfile(account));
    }

    public UserProfileResponse currentUser(String identifier) {
        UserAccount account = userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(identifier, identifier)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
        return toProfile(account);
    }

    @Transactional
    public CommonMessageResponse requestPasswordReset(PasswordResetRequest request) {
        UserAccount account = userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(request.identifier(), request.identifier())
                .orElseThrow(() -> {
                    logPasswordResetEvent(null, request.identifier(), "REQUEST_PASSWORD_RESET", "FAILED", "Account not found.");
                    return new ApiException(HttpStatus.NOT_FOUND, "User not found.");
                });
        OffsetDateTime now = OffsetDateTime.now();
        if (account.getResetRequestedAt() != null && account.getResetRequestedAt().plusMinutes(1).isAfter(now)) {
            logPasswordResetEvent(account.getId(), request.identifier(), "REQUEST_PASSWORD_RESET", "FAILED", "Reset requested too frequently.");
            throw new ApiException(HttpStatus.BAD_REQUEST, "A reset code was already requested recently. Wait one minute and try again.");
        }
        String generatedCode = generateResetCode();
        account.setResetCode(generatedCode);
        account.setResetExpiry(now.plusMinutes(10));
        account.setResetRequestedAt(now);
        try {
            passwordResetEmailService.sendResetCode(account.getEmail(), generatedCode);
        } catch (ApiException exception) {
            if (!exposeResetCodeOnEmailFailure) {
                account.setResetCode(null);
                account.setResetExpiry(null);
                account.setResetRequestedAt(null);
                logPasswordResetEvent(account.getId(), request.identifier(), "REQUEST_PASSWORD_RESET", "FAILED", "Reset code cleared after email delivery failure.");
                throw exception;
            }
            logPasswordResetEvent(account.getId(), request.identifier(), "REQUEST_PASSWORD_RESET", "SUCCESS", "Reset code issued while email delivery is unavailable.");
            return new CommonMessageResponse(
                    true,
                    "Email reset is unavailable. Use this reset code: " + generatedCode + ". It expires in 10 minutes."
            );
        }
        logPasswordResetEvent(account.getId(), request.identifier(), "REQUEST_PASSWORD_RESET", "SUCCESS", "Reset code issued to registered email.");
        return new CommonMessageResponse(true, "Password reset code sent to the registered email address.");
    }

    @Transactional
    public CommonMessageResponse confirmPasswordReset(PasswordResetConfirmRequest request) {
        UserAccount account = userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(request.identifier(), request.identifier())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));

        if (account.getResetCode() == null || !account.getResetCode().equals(request.resetCode())) {
            logPasswordResetEvent(account.getId(), request.identifier(), "VERIFY_RESET_CODE", "FAILED", "Invalid reset code.");
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid reset code.");
        }
        if (account.getResetExpiry() == null || account.getResetExpiry().isBefore(OffsetDateTime.now())) {
            account.setResetCode(null);
            account.setResetExpiry(null);
            account.setResetRequestedAt(null);
            logPasswordResetEvent(account.getId(), request.identifier(), "VERIFY_RESET_CODE", "FAILED", "Expired reset code.");
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reset code has expired.");
        }

        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        account.setResetCode(null);
        account.setResetExpiry(null);
        account.setResetRequestedAt(null);
        account.setLastPasswordReset(OffsetDateTime.now());
        account.setMustChangePassword(false);
        logPasswordResetEvent(account.getId(), request.identifier(), "RESET_PASSWORD_SUCCESS", "SUCCESS", "Password reset completed.");
        return new CommonMessageResponse(true, "Password updated successfully.");
    }

    @Transactional
    public CommonMessageResponse changeInitialPassword(String identifier, InitialPasswordChangeRequest request) {
        UserAccount account = userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(identifier, identifier)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));

        if (!account.isMustChangePassword()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This account is not waiting for an initial password change.");
        }

        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        account.setMustChangePassword(false);
        account.setResetCode(null);
        account.setResetExpiry(null);
        account.setResetRequestedAt(null);
        account.setLastPasswordReset(OffsetDateTime.now());
        return new CommonMessageResponse(true, "Password updated successfully.");
    }

    @Transactional
    public CommonMessageResponse completeBootstrapAdmin(String identifier) {
        UserAccount account = userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(identifier, identifier)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));

        if (!isBootstrapTemporaryAccount(account)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the bootstrap admin can complete this setup step.");
        }

        long permanentAdmins = userRepository.countByRoleAndTemporaryFalseAndStatus(UserRole.ADMIN, UserStatus.ACTIVE);
        long permanentUsers = userRepository.countByRoleAndTemporaryFalseAndStatus(UserRole.USER, UserStatus.ACTIVE);
        if (permanentAdmins < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Create at least one permanent ADMIN account before finishing setup.");
        }
        if (permanentUsers < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Create at least one permanent USER account before finishing setup.");
        }

        userRepository.findAll().stream()
                .filter(this::isBootstrapTemporaryAccount)
                .forEach(user -> {
            user.setStatus(UserStatus.FROZEN);
            user.setMustChangePassword(false);
            user.setResetCode(null);
            user.setResetExpiry(null);
            user.setResetRequestedAt(null);
        });
        return new CommonMessageResponse(true, "Temporary bootstrap accounts disabled. Sign in with a permanent account.");
    }

    private UserProfileResponse toProfile(UserAccount account) {
        return new UserProfileResponse(
                account.getId(),
                account.getFullName(),
                account.getUsername(),
                account.getRole().name(),
                account.getDepartment(),
                account.getEmail(),
                account.getStatus().name(),
                account.isMustChangePassword()
        );
    }

    private String generateResetCode() {
        int code = (int) (Math.random() * 900000) + 100000;
        return Integer.toString(code);
    }

    private boolean isPrimarySuperAdmin(UserAccount account) {
        return account != null
                && account.getEmail() != null
                && PRIMARY_SUPER_ADMIN_EMAIL.equalsIgnoreCase(account.getEmail().trim());
    }

    private boolean isBootstrapTemporaryAccount(UserAccount account) {
        return account != null
                && account.isTemporary()
                && account.getEmail() != null
                && BOOTSTRAP_EMAILS.contains(account.getEmail().trim().toLowerCase());
    }

    private void logPasswordResetEvent(Long userId, String identifier, String eventType, String status, String details) {
        jdbcTemplate.update(
                "INSERT INTO password_reset_audit (user_id, identifier, event_type, status, details) VALUES (?, ?, ?, ?, ?)",
                userId,
                normalize(identifier),
                eventType,
                status,
                details
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
