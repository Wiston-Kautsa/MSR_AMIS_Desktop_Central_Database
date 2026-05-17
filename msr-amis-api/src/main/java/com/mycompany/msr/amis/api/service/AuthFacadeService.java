package com.mycompany.msr.amis.api.service;

import com.mycompany.msr.amis.api.config.ReservedEmailConfig;
import com.mycompany.msr.amis.api.domain.UserAccount;
import com.mycompany.msr.amis.api.domain.UserRole;
import com.mycompany.msr.amis.api.domain.UserStatus;
import com.mycompany.msr.amis.api.dto.CommonMessageResponse;
import com.mycompany.msr.amis.api.dto.auth.InitialAdminSetupRequest;
import com.mycompany.msr.amis.api.dto.auth.InitialPasswordChangeRequest;
import com.mycompany.msr.amis.api.dto.auth.LoginRequest;
import com.mycompany.msr.amis.api.dto.auth.LoginResponse;
import com.mycompany.msr.amis.api.dto.auth.PasswordResetConfirmRequest;
import com.mycompany.msr.amis.api.dto.auth.PasswordResetRequest;
import com.mycompany.msr.amis.api.dto.auth.UserProfileResponse;
import com.mycompany.msr.amis.api.exception.ApiException;
import com.mycompany.msr.amis.api.repository.UserRepository;
import com.mycompany.msr.amis.api.security.JwtService;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthFacadeService {

    private static final SecureRandom RESET_RANDOM = new SecureRandom();
    private static final String RESET_TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetEmailService passwordResetEmailService;
    private final JdbcTemplate jdbcTemplate;
    private final ActionAuditService actionAuditService;
    private final ReservedEmailConfig reservedEmailConfig;
    private final boolean exposeResetCodeOnEmailFailure;

    public AuthFacadeService(UserRepository userRepository,
                             JwtService jwtService,
                             PasswordEncoder passwordEncoder,
                             PasswordResetEmailService passwordResetEmailService,
                             JdbcTemplate jdbcTemplate,
                             ActionAuditService actionAuditService,
                             ReservedEmailConfig reservedEmailConfig,
                             @org.springframework.beans.factory.annotation.Value("${app.security.password-reset.expose-code-when-email-disabled:true}")
                             boolean exposeResetCodeOnEmailFailure) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetEmailService = passwordResetEmailService;
        this.jdbcTemplate = jdbcTemplate;
        this.actionAuditService = actionAuditService;
        this.reservedEmailConfig = reservedEmailConfig;
        this.exposeResetCodeOnEmailFailure = exposeResetCodeOnEmailFailure;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserAccount account = userRepository
                .findByEmailIgnoreCaseOrUsernameIgnoreCase(request.identifier(), request.identifier())
                .orElseThrow(() -> {
                    actionAuditService.log(request.identifier(), "LOGIN_FAILED", "AUTH", request.identifier(), "Unknown login identifier.");
                    return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
                });

        if (isPrimarySuperAdmin(account) && account.isMustChangePassword()) {
            actionAuditService.log(account.getEmail(), "LOGIN_FAILED", "AUTH", account.getEmail(), "Primary super admin must reset password before sign in.");
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "The primary super admin must reset the password before signing in."
            );
        }

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            actionAuditService.log(account.getEmail(), "LOGIN_FAILED", "AUTH", account.getEmail(), "Invalid password.");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }

        if (account.getStatus() != UserStatus.ACTIVE) {
            actionAuditService.log(account.getEmail(), "LOGIN_FAILED", "AUTH", account.getEmail(), "Inactive account login blocked.");
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

        actionAuditService.log(account.getEmail(), "LOGIN_SUCCESS", "AUTH", account.getEmail(), "User logged in successfully.");
        return new LoginResponse(token, toProfile(account));
    }

    @Transactional
    public CommonMessageResponse setupInitialAdmin(InitialAdminSetupRequest request) {
        long permanentAdmins = userRepository.countByRoleAndTemporaryFalseAndStatus(UserRole.SUPER_ADMIN, UserStatus.ACTIVE)
                + userRepository.countByRoleAndTemporaryFalseAndStatus(UserRole.ADMIN, UserStatus.ACTIVE);
        if (permanentAdmins > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Initial setup is already complete.");
        }

        String email = normalize(request.email()).toLowerCase();
        String username = normalize(request.username());
        if (reservedEmailConfig.isReservedEmail(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use an organisation email that is not a reserved setup account.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already exists.");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "Username already exists.");
        }

        UserAccount account = new UserAccount();
        account.setFullName(normalize(request.fullName()));
        account.setUsername(username);
        account.setEmail(email);
        account.setDepartment(normalize(request.department()).isBlank() ? "MSR" : normalize(request.department()));
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setRole(UserRole.SUPER_ADMIN);
        account.setStatus(UserStatus.ACTIVE);
        account.setTemporary(false);
        account.setMustChangePassword(false);
        userRepository.save(account);

        freezeBootstrapAccounts();
        actionAuditService.log(email, "INITIAL_ADMIN_CREATED", "AUTH", email, "First production super admin created.");
        return new CommonMessageResponse(true, "Initial super admin created. Sign in with the new account.");
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
                    actionAuditService.log(request.identifier(), "PASSWORD_RESET_REQUEST_FAILED", "AUTH", request.identifier(), "Password reset requested for unknown account.");
                    return new ApiException(HttpStatus.NOT_FOUND, "User not found.");
                });
        OffsetDateTime now = OffsetDateTime.now();
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
                actionAuditService.log(account.getEmail(), "PASSWORD_RESET_REQUEST_FAILED", "AUTH", account.getEmail(), "Password reset email delivery failed.");
                throw exception;
            }
            logPasswordResetEvent(account.getId(), request.identifier(), "REQUEST_PASSWORD_RESET", "SUCCESS", "Reset code issued while email delivery is unavailable.");
            actionAuditService.log(account.getEmail(), "PASSWORD_RESET_REQUESTED", "AUTH", account.getEmail(), "Password reset code issued while email delivery is unavailable.");
            return new CommonMessageResponse(
                    true,
                    "Email reset is unavailable. Use this reset code: " + generatedCode + ". It expires in 10 minutes."
            );
        }
        logPasswordResetEvent(account.getId(), request.identifier(), "REQUEST_PASSWORD_RESET", "SUCCESS", "Reset code issued to registered email.");
        actionAuditService.log(account.getEmail(), "PASSWORD_RESET_REQUESTED", "AUTH", account.getEmail(), "Password reset requested.");
        return new CommonMessageResponse(true, "Password reset code sent to " + account.getEmail() + ".");
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
        actionAuditService.log(account.getEmail(), "PASSWORD_RESET_SUCCESS", "AUTH", account.getEmail(), "Password reset completed.");
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
        actionAuditService.log(account.getEmail(), "INITIAL_PASSWORD_CHANGED", "AUTH", account.getEmail(), "Initial password changed successfully.");
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

        freezeBootstrapAccounts();
        return new CommonMessageResponse(true, "Temporary bootstrap accounts disabled. Sign in with a permanent account.");
    }

    private void freezeBootstrapAccounts() {
        userRepository.findAll().stream()
                .filter(this::isBootstrapTemporaryAccount)
                .forEach(user -> {
                    user.setStatus(UserStatus.FROZEN);
                    user.setMustChangePassword(false);
                    user.setResetCode(null);
                    user.setResetExpiry(null);
                    user.setResetRequestedAt(null);
                });
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
        StringBuilder token = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            token.append(RESET_TOKEN_ALPHABET.charAt(RESET_RANDOM.nextInt(RESET_TOKEN_ALPHABET.length())));
        }
        return token.toString();
    }

    private boolean isPrimarySuperAdmin(UserAccount account) {
        return account != null
                && account.getEmail() != null
                && reservedEmailConfig.isPrimarySuperAdminEmail(account.getEmail());
    }

    private boolean isBootstrapTemporaryAccount(UserAccount account) {
        return account != null
                && account.isTemporary()
                && account.getEmail() != null
                && reservedEmailConfig.isBootstrapEmail(account.getEmail());
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
