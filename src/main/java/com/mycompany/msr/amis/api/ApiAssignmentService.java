package com.mycompany.msr.amis;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ApiAssignmentService implements AssignmentService {

    private final ApiClient apiClient;

    public ApiAssignmentService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public List<Assignment> getAssignments() {
        try {
            AssignmentPayload[] payloads = apiClient.get("/api/assignments", AssignmentPayload[].class);
            return Arrays.stream(payloads == null ? new AssignmentPayload[0] : payloads)
                    .map(AssignmentPayload::toAssignment)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IllegalStateException(resolveMessage(e), e);
        }
    }

    @Override
    public List<Assignment> getAssignmentsPendingReturn() {
        try {
            AssignmentPayload[] payloads = apiClient.get("/api/assignments/pending-returns", AssignmentPayload[].class);
            return Arrays.stream(payloads == null ? new AssignmentPayload[0] : payloads)
                    .map(AssignmentPayload::toAssignment)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IllegalStateException(resolveMessage(e), e);
        }
    }

    @Override
    public Map<String, Integer> getAvailableStockByCategory() {
        try {
            JsonNode node = apiClient.getJson("/api/assignments/available-stock");
            Map<String, Integer> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asInt()));
            return result;
        } catch (Exception e) {
            throw new IllegalStateException(resolveMessage(e), e);
        }
    }

    @Override
    public int getAvailableStock(String equipmentType) {
        return getAvailableStockByCategory().getOrDefault(equipmentType, 0);
    }

    @Override
    public int getDistributedCountForAssignment(int assignmentId) {
        try {
            JsonNode node = apiClient.getJson("/api/assignments/" + assignmentId + "/distributed-count");
            return node.path("count").asInt(0);
        } catch (Exception e) {
            throw new IllegalStateException(resolveMessage(e), e);
        }
    }

    @Override
    public void createAssignment(String person, String department, String equipmentType, String reason, int quantity) throws Exception {
        apiClient.post("/api/assignments", new AssignmentRequest(person, department, equipmentType, reason, quantity), AssignmentPayload.class);
        refreshLocalMirror();
    }

    @Override
    public void updateAssignment(int id, String person, String department, String equipmentType, String reason, int quantity) throws Exception {
        apiClient.put("/api/assignments/" + id, new AssignmentRequest(person, department, equipmentType, reason, quantity), AssignmentPayload.class);
        refreshLocalMirror();
    }

    @Override
    public void updateAssignmentStatus(int id, String status) throws Exception {
        apiClient.patch("/api/assignments/" + id + "/status", new StatusRequest(status), AssignmentPayload.class);
        refreshLocalMirror();
    }

    @Override
    public void deleteAssignment(int id) throws Exception {
        apiClient.delete("/api/assignments/" + id);
        refreshLocalMirror();
    }

    private void refreshLocalMirror() {
        ServiceRegistry.getRemoteMirrorCoordinator().refreshAfterRemoteMutation();
    }

    private String resolveMessage(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank() ? "Failed to call the assignment API." : e.getMessage();
    }

    private static final class AssignmentRequest {
        public String person;
        public String department;
        public String equipmentType;
        public String reason;
        public int quantity;

        private AssignmentRequest(String person, String department, String equipmentType, String reason, int quantity) {
            this.person = person;
            this.department = department;
            this.equipmentType = equipmentType;
            this.reason = reason;
            this.quantity = quantity;
        }
    }

    private static final class AssignmentPayload {
        public int id;
        public String person;
        public String department;
        public String equipmentType;
        public String reason;
        public int quantity;
        public String date;
        public int distributedCount;
        public String status;

        private Assignment toAssignment() {
            Assignment assignment = new Assignment(id, person, department, equipmentType, reason, quantity, date);
            assignment.setDistributedCount(distributedCount);
            assignment.setStatus(status);
            return assignment;
        }
    }

    private static final class StatusRequest {
        public String status;

        private StatusRequest(String status) {
            this.status = status;
        }
    }
}
