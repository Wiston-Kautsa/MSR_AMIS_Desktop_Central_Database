package com.mycompany.msr.amis;

public final class ApiAuthService implements AuthService {

    private final ApiClient apiClient;

    public ApiAuthService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public User authenticate(String identifier, String password) {
        try {
            LoginRequest request = new LoginRequest(identifier, password);
            LoginResponse response = apiClient.post("/api/auth/login", request, LoginResponse.class);
            Session.setAuthToken(response.token);
            Session.setPasswordChangeRequired(response.user != null && response.user.mustChangePassword);
            return response.user.toUser();
        } catch (ApiClientException exception) {
            if (exception.getStatusCode() == 401 || exception.getStatusCode() == 403) {
                throw new SecurityException(exception.getMessage());
            }
            throw new IllegalStateException(exception.getMessage(), exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to reach the API server.", exception);
        }
    }

    @Override
    public boolean isTemporarySetupAccount(User user) {
        return user != null && AccessControl.isTemporarySetupAccountEmail(user.getEmail());
    }

    @Override
    public String issuePasswordResetCode(String identifier, String resetCode, java.time.LocalDateTime expiresAt) throws Exception {
        PasswordResetMessageResponse response = apiClient.post(
                "/api/auth/password-reset/request",
                new PasswordResetRequest(identifier),
                PasswordResetMessageResponse.class
        );
        return response == null || response.message == null || response.message.isBlank()
                ? "Password reset code generated."
                : response.message;
    }

    @Override
    public void clearPasswordResetCode(String identifier) {
        // The API owns reset code lifecycle; no desktop-side cleanup call is needed.
    }

    @Override
    public void resetPasswordWithCode(String identifier, String resetCode, String newPassword) throws Exception {
        apiClient.post(
                "/api/auth/password-reset/confirm",
                new PasswordResetConfirmRequest(identifier, resetCode, newPassword),
                PasswordResetMessageResponse.class
        );
    }

    @Override
    public void completeInitialPasswordChange(String newPassword) throws Exception {
        apiClient.post(
                "/api/auth/initial-password/change",
                new InitialPasswordChangeRequest(newPassword),
                PasswordResetMessageResponse.class
        );
        Session.setPasswordChangeRequired(false);
    }

    public static final class LoginRequest {
        public String identifier;
        public String password;

        public LoginRequest() {
        }

        public LoginRequest(String identifier, String password) {
            this.identifier = identifier;
            this.password = password;
        }
    }

    public static final class PasswordResetRequest {
        public String identifier;

        public PasswordResetRequest() {
        }

        public PasswordResetRequest(String identifier) {
            this.identifier = identifier;
        }
    }

    public static final class PasswordResetConfirmRequest {
        public String identifier;
        public String resetCode;
        public String newPassword;

        public PasswordResetConfirmRequest() {
        }

        public PasswordResetConfirmRequest(String identifier, String resetCode, String newPassword) {
            this.identifier = identifier;
            this.resetCode = resetCode;
            this.newPassword = newPassword;
        }
    }

    public static final class InitialPasswordChangeRequest {
        public String newPassword;

        public InitialPasswordChangeRequest() {
        }

        public InitialPasswordChangeRequest(String newPassword) {
            this.newPassword = newPassword;
        }
    }

    public static final class LoginResponse {
        public String token;
        public UserPayload user;
    }

    public static final class UserPayload {
        public Long id;
        public String fullName;
        public String username;
        public String role;
        public String department;
        public String email;
        public String status;
        public boolean mustChangePassword;

        private User toUser() {
            return new User(
                    id == null ? 0 : id.intValue(),
                    fullName,
                    username,
                    "",
                    role,
                    department,
                    "",
                    email,
                    status
            );
        }
    }

    public static final class PasswordResetMessageResponse {
        public boolean success;
        public String message;
    }
}
