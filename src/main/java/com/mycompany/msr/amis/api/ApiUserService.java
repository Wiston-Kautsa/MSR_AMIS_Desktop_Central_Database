package com.mycompany.msr.amis;

import java.util.Arrays;
import java.util.List;
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
            return Arrays.stream(users == null ? new UserPayload[0] : users)
                    .map(UserPayload::toUser)
                    .collect(Collectors.toList());
        } catch (Exception exception) {
            throw new IllegalStateException(resolveMessage(exception), exception);
        }
    }

    @Override
    public List<String> getDepartments() {
        try {
            String[] departments = apiClient.get("/api/departments", String[].class);
            return Arrays.asList(departments == null ? new String[0] : departments);
        } catch (Exception exception) {
            return List.of("MSR");
        }
    }

    @Override
    public void createUser(String name, String password, String role, String department, String email) throws Exception {
        String username = deriveUsername(email);
        apiClient.post("/api/users", new UserRequest(name, username, email, role, department, "", password), UserPayload.class);
    }

    @Override
    public boolean updateUser(int id, String name, String password, String role, String department, String email) throws Exception {
        String username = deriveUsername(email);
        apiClient.put("/api/users/" + id, new UserRequest(name, username, email, role, department, "", password), UserPayload.class);
        return true;
    }

    @Override
    public boolean updateUserStatus(int id, String status) throws Exception {
        apiClient.patch("/api/users/" + id + "/status", new StatusRequest(status), UserPayload.class);
        return true;
    }

    @Override
    public void deleteUser(int id) throws Exception {
        apiClient.delete("/api/users/" + id);
    }

    @Override
    public void completeTemporarySetup(String email) throws Exception {
        apiClient.post("/api/auth/bootstrap-admin/complete", new BootstrapAdminCompletionRequest(), CompletionResponse.class);
    }

    @Override
    public String resetRemoteDemoData() throws Exception {
        CompletionResponse response = apiClient.post("/api/system/demo-reset", new ResetRemoteDemoDataRequest(), CompletionResponse.class);
        return response == null || response.message == null || response.message.isBlank()
                ? "Remote demo data reset completed."
                : response.message;
    }

    private String deriveUsername(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int atIndex = email.indexOf('@');
        return atIndex > 0 ? email.substring(0, atIndex).trim().toLowerCase() : email.trim().toLowerCase();
    }

    private String resolveMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Failed to call the users API."
                : exception.getMessage();
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

    public static final class ResetRemoteDemoDataRequest {
    }

    public static final class CompletionResponse {
        public boolean success;
        public String message;
    }
}
