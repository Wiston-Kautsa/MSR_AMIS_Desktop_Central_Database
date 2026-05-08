package com.mycompany.msr.amis.api.service;

import com.mycompany.msr.amis.api.domain.UserAccount;
import com.mycompany.msr.amis.api.domain.UserRole;
import com.mycompany.msr.amis.api.domain.UserStatus;
import com.mycompany.msr.amis.api.dto.CommonMessageResponse;
import com.mycompany.msr.amis.api.dto.user.UserRequest;
import com.mycompany.msr.amis.api.dto.user.UserResponse;
import com.mycompany.msr.amis.api.exception.ApiException;
import com.mycompany.msr.amis.api.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserManagementService {

    private static final String PRIMARY_SUPER_ADMIN_EMAIL = "wkautsa@gmail.com";
    private static final Set<String> BOOTSTRAP_EMAILS = Set.of("admin@msr.local", "user@msr.local");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActionAuditService actionAuditService;

    public UserManagementService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 ActionAuditService actionAuditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.actionAuditService = actionAuditService;
    }

    public List<UserResponse> listVisibleUsers(String requesterIdentifier) {
        UserAccount requester = getRequester(requesterIdentifier);
        return userRepository.findByTemporaryFalseAndRoleInOrderByFullNameAsc(visibleRolesFor(requester)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserResponse createUser(String requesterIdentifier, UserRequest request) {
        UserAccount requester = getRequester(requesterIdentifier);
        requireManager(requester);

        UserRole targetRole = parseRole(request.role());
        assertRoleAssignmentAllowed(requester, targetRole);

        String email = normalizeEmail(request.email());
        String username = normalizeRequired(request.username(), "Username is required.");
        assertReservedEmail(email);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already exists.");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "Username already exists.");
        }
        String password = normalizeRequired(request.password(), "Password is required.");

        UserAccount user = new UserAccount();
        user.setFullName(normalizeRequired(request.fullName(), "Full name is required."));
        user.setUsername(username);
        user.setEmail(email);
        user.setRole(targetRole);
        user.setStatus(UserStatus.ACTIVE);
        user.setTemporary(false);
        user.setDepartment(normalizeRequired(request.department(), "Department is required."));
        user.setPhone(normalizeOptional(request.phone()));
        user.setPasswordHash(passwordEncoder.encode(password));

        UserAccount saved = userRepository.save(user);
        actionAuditService.log(
                requester.getEmail(),
                "CREATE_USER",
                "USERS",
                saved.getId().toString(),
                "User created: " + saved.getEmail() + ", role: " + saved.getRole().name() +
                        ", department: " + saved.getDepartment()
        );
        return toResponse(saved);
    }

    @Transactional
    public UserResponse updateUser(String requesterIdentifier, Long userId, UserRequest request) {
        UserAccount requester = getRequester(requesterIdentifier);
        requireManager(requester);

        UserAccount target = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
        assertTargetManageable(requester, target);

        UserRole newRole = parseRole(request.role());
        assertRoleAssignmentAllowed(requester, newRole);
        if (isPrimarySuperAdmin(target) && newRole != UserRole.SUPER_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "The primary super admin cannot be demoted.");
        }
        if (target.getRole() == UserRole.SUPER_ADMIN
                && newRole != UserRole.SUPER_ADMIN
                && userRepository.countByRole(UserRole.SUPER_ADMIN) <= 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The last super admin cannot be demoted.");
        }

        String email = normalizeEmail(request.email());
        String username = normalizeRequired(request.username(), "Username is required.");
        assertReservedEmail(email);
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already exists.");
        }
        if (userRepository.existsByUsernameIgnoreCaseAndIdNot(username, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Username already exists.");
        }

        String oldSnapshot = userSnapshot(target);
        target.setFullName(normalizeRequired(request.fullName(), "Full name is required."));
        target.setUsername(username);
        target.setEmail(email);
        target.setRole(newRole);
        target.setDepartment(normalizeRequired(request.department(), "Department is required."));
        target.setPhone(normalizeOptional(request.phone()));

        String password = normalizeOptional(request.password());
        if (!password.isBlank()) {
            target.setPasswordHash(passwordEncoder.encode(password));
        }

        actionAuditService.log(
                requester.getEmail(),
                "EDIT_USER",
                "USERS",
                target.getId().toString(),
                "User edited. Old: " + oldSnapshot + ". New: " + userSnapshot(target) +
                        (password.isBlank() ? "" : ". Password changed.")
        );
        return toResponse(target);
    }

    @Transactional
    public UserResponse updateStatus(String requesterIdentifier, Long userId, String rawStatus) {
        UserAccount requester = getRequester(requesterIdentifier);
        requireManager(requester);

        UserAccount target = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
        assertTargetManageable(requester, target);

        UserStatus status = parseStatus(rawStatus);
        if (isPrimarySuperAdmin(target) && status == UserStatus.FROZEN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "The primary super admin cannot be frozen.");
        }
        if (target.getRole() == UserRole.SUPER_ADMIN
                && status == UserStatus.FROZEN
                && userRepository.countByRole(UserRole.SUPER_ADMIN) <= 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The last super admin cannot be frozen.");
        }

        target.setStatus(status);
        actionAuditService.log(
                requester.getEmail(),
                "USER_" + status.name(),
                "USERS",
                target.getId().toString(),
                "User status changed to " + status.name()
        );
        return toResponse(target);
    }

    @Transactional
    public CommonMessageResponse deleteUser(String requesterIdentifier, Long userId) {
        UserAccount requester = getRequester(requesterIdentifier);
        requireManager(requester);

        UserAccount target = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
        assertTargetManageable(requester, target);

        if (isPrimarySuperAdmin(target)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "The primary super admin cannot be deleted.");
        }
        if (target.getRole() == UserRole.SUPER_ADMIN && userRepository.countByRole(UserRole.SUPER_ADMIN) <= 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The last super admin cannot be deleted.");
        }
        if (target.getRole() == UserRole.ADMIN && userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The last admin cannot be deleted.");
        }

        String oldSnapshot = userSnapshot(target);
        userRepository.delete(target);
        actionAuditService.log(
                requester.getEmail(),
                "DELETE_USER",
                "USERS",
                userId.toString(),
                "User deleted. Old: " + oldSnapshot
        );
        return new CommonMessageResponse(true, "User deleted successfully.");
    }

    public UserAccount getUser(String requesterIdentifier) {
        return userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(requesterIdentifier, requesterIdentifier)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found."));
    }

    private UserAccount getRequester(String requesterIdentifier) {
        return getUser(requesterIdentifier);
    }

    private List<UserRole> visibleRolesFor(UserAccount requester) {
        if (isBootstrapTemporaryAccount(requester)) {
            return List.of(UserRole.ADMIN, UserRole.USER);
        }
        if (requester.getRole() == UserRole.SUPER_ADMIN) {
            return List.of(UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.USER);
        }
        if (requester.getRole() == UserRole.ADMIN) {
            return List.of(UserRole.ADMIN, UserRole.USER);
        }
        return List.of(UserRole.USER);
    }

    private void requireManager(UserAccount requester) {
        if (isBootstrapTemporaryAccount(requester)) {
            return;
        }
        if (requester.getRole() == UserRole.USER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Users are not allowed to manage accounts.");
        }
    }

    private void assertTargetManageable(UserAccount requester, UserAccount target) {
        if (requester.getRole() == UserRole.SUPER_ADMIN) {
            if (target.getRole() == UserRole.SUPER_ADMIN && !isPrimarySuperAdmin(requester) && isPrimarySuperAdmin(target)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Only the primary super admin can manage that account.");
            }
            return;
        }
        if (requester.getRole() == UserRole.ADMIN && target.getRole() == UserRole.SUPER_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admins cannot manage super admins.");
        }
        if (requester.getRole() == UserRole.ADMIN && isPrimarySuperAdmin(target)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admins cannot manage the primary super admin.");
        }
    }

    private void assertRoleAssignmentAllowed(UserAccount requester, UserRole targetRole) {
        if (isBootstrapTemporaryAccount(requester)) {
            if (targetRole == UserRole.SUPER_ADMIN) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Bootstrap accounts cannot assign the SUPER_ADMIN role.");
            }
            return;
        }
        if (targetRole == UserRole.SUPER_ADMIN && !isPrimarySuperAdmin(requester)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the primary super admin can assign the SUPER_ADMIN role.");
        }
        if (requester.getRole() == UserRole.ADMIN && targetRole == UserRole.SUPER_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admins cannot assign the SUPER_ADMIN role.");
        }
    }

    private UserRole parseRole(String rawRole) {
        String normalized = normalizeRequired(rawRole, "Role is required.").toUpperCase(Locale.ROOT);
        try {
            return UserRole.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid role.");
        }
    }

    private UserStatus parseStatus(String rawStatus) {
        String normalized = normalizeRequired(rawStatus, "Status is required.").toUpperCase(Locale.ROOT);
        try {
            return UserStatus.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid status.");
        }
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private String userSnapshot(UserAccount user) {
        if (user == null) {
            return "not found";
        }
        return "id=" + user.getId() +
                ", email=" + normalizeOptional(user.getEmail()) +
                ", username=" + normalizeOptional(user.getUsername()) +
                ", fullName=" + normalizeOptional(user.getFullName()) +
                ", role=" + (user.getRole() == null ? "" : user.getRole().name()) +
                ", department=" + normalizeOptional(user.getDepartment()) +
                ", status=" + (user.getStatus() == null ? "" : user.getStatus().name());
    }

    private String normalizeEmail(String email) {
        return normalizeRequired(email, "Email is required.").toLowerCase(Locale.ROOT);
    }

    private void assertReservedEmail(String email) {
        if (BOOTSTRAP_EMAILS.contains(email) || PRIMARY_SUPER_ADMIN_EMAIL.equals(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This email is reserved for a temporary system account.");
        }
    }

    private boolean isPrimarySuperAdmin(UserAccount user) {
        return user != null && normalizeEmail(user.getEmail()).equals(PRIMARY_SUPER_ADMIN_EMAIL);
    }

    private boolean isBootstrapTemporaryAccount(UserAccount user) {
        if (user == null || !user.isTemporary() || user.getEmail() == null) {
            return false;
        }
        return BOOTSTRAP_EMAILS.contains(normalizeEmail(user.getEmail()));
    }

    private UserResponse toResponse(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getRole().name(),
                user.getDepartment(),
                user.getPhone(),
                user.getEmail(),
                user.getStatus().name()
        );
    }
}
