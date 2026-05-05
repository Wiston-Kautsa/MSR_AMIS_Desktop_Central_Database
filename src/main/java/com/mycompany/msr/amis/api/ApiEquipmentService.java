package com.mycompany.msr.amis;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class ApiEquipmentService implements EquipmentService {

    private final ApiClient apiClient;

    public ApiEquipmentService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public List<Equipment> getAllEquipment() {
        try {
            EquipmentPayload[] payloads = apiClient.get("/api/equipment", EquipmentPayload[].class);
            return Arrays.stream(payloads == null ? new EquipmentPayload[0] : payloads)
                    .map(EquipmentPayload::toEquipment)
                    .collect(Collectors.toList());
        } catch (Exception exception) {
            throw new IllegalStateException(resolveMessage(exception), exception);
        }
    }

    @Override
    public List<String> getEquipmentCategories() {
        try {
            String[] categories = apiClient.get("/api/equipment/categories", String[].class);
            return Arrays.asList(categories == null ? new String[0] : categories);
        } catch (Exception exception) {
            throw new IllegalStateException(resolveMessage(exception), exception);
        }
    }

    @Override
    public void createEquipment(Equipment equipment) throws Exception {
        apiClient.post("/api/equipment", EquipmentRequest.from(equipment), EquipmentPayload.class);
        refreshLocalMirror();
    }

    @Override
    public void updateEquipment(String assetCode, String serialNumber, String name, String category, String condition) throws Exception {
        EquipmentPayload existing = apiClient.get("/api/equipment/" + assetCode, EquipmentPayload.class);
        EquipmentRequest request = new EquipmentRequest();
        request.name = name;
        request.category = category;
        request.serialNumber = serialNumber;
        request.source = existing == null ? "" : existing.source;
        request.condition = condition;
        request.entryDate = existing == null ? null : existing.entryDate;
        apiClient.put("/api/equipment/" + assetCode, request, EquipmentPayload.class);
        refreshLocalMirror();
    }

    @Override
    public void updateEquipmentStatus(String assetCode, String status) throws Exception {
        apiClient.patch("/api/equipment/" + assetCode + "/status", new StatusRequest(status), EquipmentPayload.class);
        refreshLocalMirror();
    }

    @Override
    public void deleteEquipment(String assetCode) throws Exception {
        apiClient.delete("/api/equipment/" + assetCode);
        refreshLocalMirror();
    }

    private void refreshLocalMirror() {
        ServiceRegistry.getRemoteMirrorCoordinator().refreshAfterRemoteMutation();
    }

    private String resolveMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Failed to call the equipment API."
                : exception.getMessage();
    }

    public static final class EquipmentRequest {
        public String name;
        public String category;
        public String serialNumber;
        public String source;
        public String condition;
        public String entryDate;

        static EquipmentRequest from(Equipment equipment) {
            EquipmentRequest request = new EquipmentRequest();
            request.name = equipment.getName();
            request.category = equipment.getCategory();
            request.serialNumber = equipment.getSerialNumber();
            request.source = equipment.getSource();
            request.condition = equipment.getCondition();
            request.entryDate = equipment.getEntryDate();
            return request;
        }
    }

    public static final class EquipmentPayload {
        public Long id;
        public String assetCode;
        public String name;
        public String category;
        public String serialNumber;
        public String condition;
        public String source;
        public String entryDate;
        public String status;

        private Equipment toEquipment() {
            return new Equipment(
                    id == null ? 0 : id.intValue(),
                    assetCode,
                    serialNumber,
                    name,
                    category,
                    condition,
                    source,
                    entryDate,
                    status
            );
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
}
