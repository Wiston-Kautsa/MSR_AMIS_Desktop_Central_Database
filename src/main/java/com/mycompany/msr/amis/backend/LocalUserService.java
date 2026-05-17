package com.mycompany.msr.amis;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

public final class LocalUserService implements UserService {

    private final RemoteMirrorCoordinator remoteMirrorCoordinator = ServiceRegistry.getRemoteMirrorCoordinator();

    @Override
    public List<User> getUsers() {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        return DatabaseHandler.getUsers();
    }

    @Override
    public List<String> getDepartments() {
        return ServiceRegistry.getDepartmentService().getDepartments();
    }

    @Override
    public void createUser(String name, String password, String role, String department, String email) throws Exception {
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            remoteMirrorCoordinator.getRemoteUserService().createUser(name, password, role, department, email);
            remoteMirrorCoordinator.synchronizeFromRemote(passwordOverride(email, password));
            return;
        }
        DatabaseHandler.insertUser(name, PasswordUtils.hash(password), role, department, email);
        ServiceRegistry.getSyncCenterService().queueOperation(
                "USER",
                "CREATE",
                email,
                Map.of(
                        "name", name,
                        "password", password,
                        "role", role,
                        "department", department,
                        "email", email
                ),
                null,
                "Offline user creation captured for later sync."
        );
    }

    @Override
    public boolean updateUser(int id, String name, String password, String role, String department, String email) throws Exception {
        User existing = DatabaseHandler.getUserById(id);
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            remoteMirrorCoordinator.getRemoteUserService().updateUser(id, name, password, role, department, email);
            remoteMirrorCoordinator.synchronizeFromRemote(passwordOverride(email, password));
            return true;
        }
        String hashedPassword = password == null || password.isBlank() ? "" : PasswordUtils.hash(password);
        boolean updated = DatabaseHandler.updateUser(id, name, hashedPassword, role, department, email);
        if (updated) {
            ServiceRegistry.getSyncCenterService().queueOperation(
                    "USER",
                    "UPDATE",
                    email,
                    Map.of(
                            "name", name,
                            "password", password == null ? "" : password,
                            "role", role,
                            "department", department,
                            "email", email
                    ),
                    userPayload(existing),
                    "Offline user update captured for later sync."
            );
        }
        return updated;
    }

    @Override
    public boolean updateUserStatus(int id, String status) throws Exception {
        User existing = DatabaseHandler.getUserById(id);
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            remoteMirrorCoordinator.getRemoteUserService().updateUserStatus(id, status);
            remoteMirrorCoordinator.synchronizeFromRemote(Map.of());
            return true;
        }
        boolean updated = DatabaseHandler.updateUserStatus(id, status);
        if (updated) {
            ServiceRegistry.getSyncCenterService().queueOperation(
                    "USER",
                    "STATUS",
                    existing == null ? Integer.toString(id) : existing.getEmail(),
                    Map.of(
                            "name", existing == null ? "" : existing.getFullName(),
                            "role", existing == null ? "" : existing.getRole(),
                            "department", existing == null ? "" : existing.getDepartment(),
                            "email", existing == null ? "" : existing.getEmail(),
                            "status", status
                    ),
                    userPayload(existing),
                    "Offline user status change captured for later sync."
            );
        }
        return updated;
    }

    @Override
    public void deleteUser(int id) throws Exception {
        AccessControl.requireRole(AccessControl.ROLE_SUPER_ADMIN);
        User existing = DatabaseHandler.getUserById(id);
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            remoteMirrorCoordinator.getRemoteUserService().deleteUser(id);
            remoteMirrorCoordinator.synchronizeFromRemote(Map.of());
            return;
        }
        DatabaseHandler.deleteUser(id);
        ServiceRegistry.getSyncCenterService().queueOperation(
                "USER",
                "DELETE",
                existing == null ? Integer.toString(id) : existing.getEmail(),
                userPayload(existing),
                userPayload(existing),
                "Offline user deletion captured for later sync."
        );
    }

    @Override
    public void completeTemporarySetup(String email) throws Exception {
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            remoteMirrorCoordinator.getRemoteUserService().completeTemporarySetup(email);
            remoteMirrorCoordinator.synchronizeFromRemote(Map.of());
            return;
        }
        DatabaseHandler.completeTemporarySetup(email);
    }

    private Map<String, String> passwordOverride(String email, String plainPassword) {
        Map<String, String> overrides = new HashMap<>();
        if (email != null && !email.isBlank() && plainPassword != null && !plainPassword.isBlank()) {
            overrides.put(email.trim().toLowerCase(), PasswordUtils.hash(plainPassword));
        }
        return overrides;
    }

    private Map<String, Object> userPayload(User user) {
        if (user == null) {
            return Map.of();
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", user.getFullName());
        payload.put("role", user.getRole());
        payload.put("department", user.getDepartment());
        payload.put("email", user.getEmail());
        payload.put("status", user.getStatus());
        return payload;
    }
}
