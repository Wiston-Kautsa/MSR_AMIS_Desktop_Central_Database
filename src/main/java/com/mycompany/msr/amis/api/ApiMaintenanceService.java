package com.mycompany.msr.amis;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class ApiMaintenanceService implements MaintenanceService {

    private final ApiClient apiClient;

    public ApiMaintenanceService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public List<MaintenanceRecord> getMaintenanceRecords() {
        try {
            MaintenancePayload[] payloads = apiClient.get("/api/maintenance", MaintenancePayload[].class);
            return Arrays.stream(payloads == null ? new MaintenancePayload[0] : payloads)
                    .map(MaintenancePayload::toRecord)
                    .collect(Collectors.toList());
        } catch (Exception exception) {
            throw new IllegalStateException(resolveMessage(exception), exception);
        }
    }

    @Override
    public void createMaintenanceRecord(String assetCode,
                                        String issue,
                                        String actionTaken,
                                        String performedBy,
                                        String maintenanceDate,
                                        String cost,
                                        boolean completed) throws Exception {
        apiClient.post(
                "/api/maintenance",
                new MaintenanceRequest(assetCode, issue, actionTaken, performedBy, maintenanceDate, cost, completed),
                MaintenancePayload.class
        );
        ServiceRegistry.getRemoteMirrorCoordinator().refreshAfterRemoteMutation();
    }

    private String resolveMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Failed to call the maintenance API."
                : exception.getMessage();
    }

    public static final class MaintenanceRequest {
        public String assetCode;
        public String issue;
        public String actionTaken;
        public String performedBy;
        public String maintenanceDate;
        public String cost;
        public boolean completed;

        public MaintenanceRequest() {
        }

        public MaintenanceRequest(String assetCode,
                                  String issue,
                                  String actionTaken,
                                  String performedBy,
                                  String maintenanceDate,
                                  String cost,
                                  boolean completed) {
            this.assetCode = assetCode;
            this.issue = issue;
            this.actionTaken = actionTaken;
            this.performedBy = performedBy;
            this.maintenanceDate = maintenanceDate;
            this.cost = cost;
            this.completed = completed;
        }
    }

    public static final class MaintenancePayload {
        public int id;
        public String assetCode;
        public String issue;
        public String actionTaken;
        public String performedBy;
        public String maintenanceDate;
        public String cost;
        public String status;

        private MaintenanceRecord toRecord() {
            return new MaintenanceRecord(id, assetCode, issue, actionTaken, performedBy, maintenanceDate, cost, status);
        }
    }
}
