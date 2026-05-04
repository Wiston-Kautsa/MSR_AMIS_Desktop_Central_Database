package com.mycompany.msr.amis;

public final class ServiceRegistry {

    private static final AppConfiguration CONFIGURATION = AppConfiguration.load();
    private static final ApiClient API_CLIENT = createApiClient();
    private static final RemoteMirrorCoordinator REMOTE_MIRROR_COORDINATOR = createRemoteMirrorCoordinator();
    private static final AuthService AUTH_SERVICE = createAuthService();
    private static final EquipmentService EQUIPMENT_SERVICE = createEquipmentService();
    private static final UserService USER_SERVICE = createUserService();
    private static final AssignmentService ASSIGNMENT_SERVICE = createAssignmentService();
    private static final DistributionService DISTRIBUTION_SERVICE = createDistributionService();
    private static final ReturnService RETURN_SERVICE = createReturnService();
    private static final DashboardService DASHBOARD_SERVICE = createDashboardService();
    private static final ReportService REPORT_SERVICE = createReportService();
    private static final AssetHistoryService ASSET_HISTORY_SERVICE = createAssetHistoryService();
    private static final DataMaintenanceService DATA_MAINTENANCE_SERVICE = createDataMaintenanceService();
    private static final SyncCenterService SYNC_CENTER_SERVICE = createSyncCenterService();

    private ServiceRegistry() {
    }

    private static ApiClient createApiClient() {
        String apiBaseUrl = CONFIGURATION.getApiBaseUrl();
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            return null;
        }
        return new ApiClient(apiBaseUrl);
    }

    private static RemoteMirrorCoordinator createRemoteMirrorCoordinator() {
        return new RemoteMirrorCoordinator(CONFIGURATION, API_CLIENT);
    }

    public static AppConfiguration getConfiguration() {
        return CONFIGURATION;
    }

    public static RemoteMirrorCoordinator getRemoteMirrorCoordinator() {
        return REMOTE_MIRROR_COORDINATOR;
    }

    public static AuthService getAuthService() {
        return AUTH_SERVICE;
    }

    public static EquipmentService getEquipmentService() {
        return EQUIPMENT_SERVICE;
    }

    public static UserService getUserService() {
        return USER_SERVICE;
    }

    public static AssignmentService getAssignmentService() {
        return ASSIGNMENT_SERVICE;
    }

    public static DistributionService getDistributionService() {
        return DISTRIBUTION_SERVICE;
    }

    public static ReturnService getReturnService() {
        return RETURN_SERVICE;
    }

    public static DashboardService getDashboardService() {
        return DASHBOARD_SERVICE;
    }

    public static ReportService getReportService() {
        return REPORT_SERVICE;
    }

    public static AssetHistoryService getAssetHistoryService() {
        return ASSET_HISTORY_SERVICE;
    }

    public static DataMaintenanceService getDataMaintenanceService() {
        return DATA_MAINTENANCE_SERVICE;
    }

    public static SyncCenterService getSyncCenterService() {
        return SYNC_CENTER_SERVICE;
    }

    private static AuthService createAuthService() {
        if (CONFIGURATION.usesLocalDatabase()) {
            return new LocalAuthService();
        }
        return new ApiAuthService(API_CLIENT);
    }

    private static EquipmentService createEquipmentService() {
        if (CONFIGURATION.usesLocalDatabase()) {
            return new LocalEquipmentService();
        }
        return new ApiEquipmentService(API_CLIENT);
    }

    private static UserService createUserService() {
        if (CONFIGURATION.usesLocalDatabase()) {
            return new LocalUserService();
        }
        return new ApiUserService(API_CLIENT);
    }

    private static AssignmentService createAssignmentService() {
        if (CONFIGURATION.usesLocalDatabase()) {
            return new LocalAssignmentService();
        }
        return new ApiAssignmentService(API_CLIENT);
    }

    private static DistributionService createDistributionService() {
        if (CONFIGURATION.usesLocalDatabase()) {
            return new LocalDistributionService();
        }
        return new ApiDistributionService(API_CLIENT);
    }

    private static ReturnService createReturnService() {
        if (CONFIGURATION.usesLocalDatabase()) {
            return new LocalReturnService();
        }
        return new ApiReturnService(API_CLIENT);
    }

    private static DashboardService createDashboardService() {
        if (CONFIGURATION.usesLocalDatabase()) {
            return new LocalDashboardService();
        }
        return new ApiDashboardService(API_CLIENT);
    }

    private static ReportService createReportService() {
        if (CONFIGURATION.usesLocalDatabase()) {
            return new LocalReportService();
        }
        return new ApiReportService(API_CLIENT);
    }

    private static AssetHistoryService createAssetHistoryService() {
        if (CONFIGURATION.usesLocalDatabase()) {
            return new LocalAssetHistoryService();
        }
        return new ApiAssetHistoryService(API_CLIENT);
    }

    private static DataMaintenanceService createDataMaintenanceService() {
        if (CONFIGURATION.usesLocalDatabase()) {
            return new LocalDataMaintenanceService();
        }
        return new ApiDataMaintenanceService(API_CLIENT);
    }

    private static SyncCenterService createSyncCenterService() {
        return new LocalSyncCenterService();
    }
}
