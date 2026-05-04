package com.mycompany.msr.amis;

public final class ApiDashboardService implements DashboardService {

    private final ApiClient apiClient;

    public ApiDashboardService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public DashboardSummary getDashboardSummary() {
        try {
            DashboardPayload payload = apiClient.get("/api/dashboard/summary", DashboardPayload.class);
            return new DashboardSummary(
                    payload.totalAssets,
                    payload.availableAssets,
                    payload.borrowedThisMonth,
                    payload.stillInUseFromBorrowedThisMonth,
                    payload.returnedThisMonth,
                    payload.assetsInUse,
                    payload.outstandingWithRemarks
            );
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage() == null || e.getMessage().isBlank()
                    ? "Failed to load dashboard summary."
                    : e.getMessage(), e);
        }
    }

    public static final class DashboardPayload {
        public int totalAssets;
        public int availableAssets;
        public int borrowedThisMonth;
        public int stillInUseFromBorrowedThisMonth;
        public int returnedThisMonth;
        public int assetsInUse;
        public int outstandingWithRemarks;
    }
}
