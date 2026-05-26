package com.mycompany.msr.amis;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ApiUserService implements UserService {

    private final ApiClient apiClient;

    public ApiUserService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public List<User> getUsers() {
        try {
            UserPayload[] users = apiClient.get("/api/users", UserPayload[].class);
            List<User> remoteUsers = Arrays.stream(users == null ? new UserPayload[0] : users)
                    .map(UserPayload::toUser)
                    .collect(Collectors.toList());
            return remoteUsers.isEmpty() ? fallbackUsers() : remoteUsers;
        } catch (Exception exception) {
            if (shouldUseLocalUserManagementFallback(exception)) {
                return fallbackUsers();
            }
            throw new IllegalStateException(resolveMessage(exception), exception);
        }
    }

    @Override
    public List<String> getDepartments() {
        return ServiceRegistry.getDepartmentService().getDepartments();
    }

    @Override
    public void createUser(String name, String password, String role, String department, String email) throws Exception {
        String username = normalizeUsername(email);
        try {
            apiClient.post("/api/users", new UserRequest(name, username, email, role, department, "", password), UserPayload.class);
            refreshLocalMirror();
        } catch (Exception exception) {
            if (!shouldUseLocalUserManagementFallback(exception)) {
                throw exception;
            }
            DatabaseHandler.insertUser(name, PasswordUtils.hash(password), role, department, email);
            ServiceRegistry.getSyncCenterService().queueOperation(
                    "USER",
                    "CREATE",
                    email,
                    userPayload(name, role, department, email, password, null),
                    null,
                    "Offline user creation captured after remote user management access was rejected."
            );
        }
    }

    @Override
    public boolean updateUser(int id, String name, String password, String role, String department, String email) throws Exception {
        String username = normalizeUsername(email);
        User existing = DatabaseHandler.getUserById(id);
        try {
            apiClient.put("/api/users/" + id, new UserRequest(name, username, email, role, department, "", password), UserPayload.class);
            refreshLocalMirror();
            return true;
        } catch (Exception exception) {
            if (!shouldUseLocalUserManagementFallback(exception)) {
                throw exception;
            }
            String hashedPassword = password == null || password.isBlank() ? "" : PasswordUtils.hash(password);
            boolean updated = DatabaseHandler.updateUser(id, name, hashedPassword, role, department, email);
            if (updated) {
                ServiceRegistry.getSyncCenterService().queueOperation(
                        "USER",
                        "UPDATE",
                        email,
                        userPayload(name, role, department, email, password, null),
                        userPayload(existing),
                        "Offline user update captured after remote user management access was rejected."
                );
            }
            return updated;
        }
    }

    @Override
    public boolean updateUserStatus(int id, String status) throws Exception {
        User existing = DatabaseHandler.getUserById(id);
        try {
            apiClient.patch("/api/users/" + id + "/status", new StatusRequest(status), UserPayload.class);
            refreshLocalMirror();
            return true;
        } catch (Exception exception) {
            if (!shouldUseLocalUserManagementFallback(exception)) {
                throw exception;
            }
            boolean updated = DatabaseHandler.updateUserStatus(id, status);
            if (updated) {
                ServiceRegistry.getSyncCenterService().queueOperation(
                        "USER",
                        "STATUS",
                        existing == null ? Integer.toString(id) : existing.getEmail(),
                        userPayload(
                                existing == null ? "" : existing.getFullName(),
                                existing == null ? "" : existing.getRole(),
                                existing == null ? "" : existing.getDepartment(),
                                existing == null ? "" : existing.getEmail(),
                                null,
                                status
                        ),
                        userPayload(existing),
                        "Offline user status change captured after remote user management access was rejected."
                );
            }
            return updated;
        }
    }

    @Override
    public void deleteUser(int id) throws Exception {
        apiClient.delete("/api/users/" + id);
        refreshLocalMirror();
    }

    @Override
    public void completeTemporarySetup(String email) throws Exception {
        apiClient.post("/api/auth/bootstrap-admin/complete", new BootstrapAdminCompletionRequest(), CompletionResponse.class);
        refreshLocalMirror();
    }

    private void refreshLocalMirror() {
        ServiceRegistry.getRemoteMirrorCoordinator().refreshAfterRemoteMutation();
    }

    private String normalizeUsername(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        return email.trim().toLowerCase();
    }

    private String resolveMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Failed to call the users API."
                : exception.getMessage();
    }

    private boolean shouldUseLocalUserManagementFallback(Exception exception) {
        if (AccessControl.canManageUsers()
                && exception instanceof ApiClientException
                && (((ApiClientException) exception).getStatusCode() == 401
                || ((ApiClientException) exception).getStatusCode() == 403)) {
            return true;
        }
        String message = resolveMessage(exception).toLowerCase();
        return AccessControl.canManageUsers()
                && message.contains("user management")
                && message.contains("only to super admin");
    }

    private List<User> fallbackUsers() {
        List<User> localUsers = DatabaseHandler.getUsers();
        if (!localUsers.isEmpty()) {
            return localUsers;
        }
        User currentUser = Session.getCurrentUser();
        return currentUser == null ? List.of() : List.of(currentUser);
    }

    private Map<String, Object> userPayload(User user) {
        if (user == null) {
            return Map.of();
        }
        return userPayload(user.getFullName(), user.getRole(), user.getDepartment(), user.getEmail(), null, user.getStatus());
    }

    private Map<String, Object> userPayload(String name,
                                            String role,
                                            String department,
                                            String email,
                                            String password,
                                            String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name == null ? "" : name);
        payload.put("role", role == null ? "" : role);
        payload.put("department", department == null ? "" : department);
        payload.put("email", email == null ? "" : email);
        if (password != null) {
            payload.put("password", password);
        }
        if (status != null) {
            payload.put("status", status);
        }
        return payload;
    }

    public static final class UserRequest {
        public String fullName;
        public String username;
        public String email;
        public String role;
        public String department;
        public String phone;
        public String password;

        public UserRequest() {
        }

        public UserRequest(String fullName, String username, String email, String role, String department, String phone, String password) {
            this.fullName = fullName;
            this.username = username;
            this.email = email;
            this.role = role;
            this.department = department;
            this.phone = phone;
            this.password = password;
        }
    }

    public static final class StatusRequest {
        public String status;

        public StatusRequest() {
        }

        public StatusRequest(String status) {
            this.status = status;
        }
    }

    public static final class UserPayload {
        public Long id;
        public String fullName;
        public String username;
        public String role;
        public String department;
        public String phone;
        public String email;
        public String status;

        private User toUser() {
            return new User(
                    id == null ? 0 : id.intValue(),
                    fullName,
                    username,
                    "",
                    role,
                    department,
                    phone,
                    email,
                    status
            );
        }
    }

    public static final class BootstrapAdminCompletionRequest {
    }

    public static final class CompletionResponse {
        public boolean success;
        public String message;
    }
}
