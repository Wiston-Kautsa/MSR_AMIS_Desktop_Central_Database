package com.mycompany.msr.amis;

public final class ApiDataMaintenanceService implements DataMaintenanceService {

    private final ApiClient apiClient;

    public ApiDataMaintenanceService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public DataMaintenanceSummary getSummary() throws Exception {
        SummaryPayload payload = apiClient.get("/api/system/data-maintenance", SummaryPayload.class);
        if (payload == null) {
            throw new IllegalStateException("Failed to load maintenance counts.");
        }
        return new DataMaintenanceSummary(
                payload.equipmentCount,
                payload.assignmentCount,
                payload.distributionCount,
                payload.returnCount,
                payload.auditLogCount
        );
    }

    @Override
    public String resetComponent(String component) throws Exception {
        MessagePayload payload = apiClient.post(
                "/api/system/data-maintenance/reset",
                new ResetRequest(component),
                MessagePayload.class
        );
        ServiceRegistry.getRemoteMirrorCoordinator().refreshAfterRemoteMutation();
        return payload == null || payload.message == null || payload.message.isBlank()
                ? "Data maintenance operation completed."
                : payload.message;
    }

    public static final class SummaryPayload {
        public int equipmentCount;
        public int assignmentCount;
        public int distributionCount;
        public int returnCount;
        public int auditLogCount;
    }

    public static final class ResetRequest {
        public String component;

        public ResetRequest() {
        }

        public ResetRequest(String component) {
            this.component = component;
        }
    }

    public static final class MessagePayload {
        public boolean success;
        public String message;
    }
}
