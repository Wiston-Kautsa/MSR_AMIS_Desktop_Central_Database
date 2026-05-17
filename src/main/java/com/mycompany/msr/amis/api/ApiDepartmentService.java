package com.mycompany.msr.amis;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public final class ApiDepartmentService implements DepartmentService {

    private final ApiClient apiClient;

    public ApiDepartmentService(ApiClient apiClient) {
        this.apiClient = apiClient;
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
    public void createDepartment(String name) throws Exception {
        apiClient.post("/api/departments", new DepartmentRequest(name), MessageResponse.class);
        refreshLocalMirror();
    }

    @Override
    public void updateDepartment(String oldName, String newName) throws Exception {
        apiClient.put("/api/departments/" + encode(oldName), new DepartmentRequest(newName), MessageResponse.class);
        refreshLocalMirror();
    }

    @Override
    public void deleteDepartment(String name) throws Exception {
        apiClient.delete("/api/departments/" + encode(name));
        refreshLocalMirror();
    }

    private void refreshLocalMirror() {
        ServiceRegistry.getRemoteMirrorCoordinator().refreshAfterRemoteMutation();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static final class DepartmentRequest {
        public String name;

        private DepartmentRequest(String name) {
            this.name = name;
        }
    }

    private static final class MessageResponse {
        public boolean success;
        public String message;
    }
}
