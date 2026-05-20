package com.mycompany.msr.amis;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class RemoteMirrorCoordinator {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(4);
    private static final long PROBE_CACHE_TTL_MS = 30_000L;
    private static final ThreadLocal<Boolean> AUTO_MIRROR_SUPPRESSED = ThreadLocal.withInitial(() -> false);

    private final AppConfiguration configuration;
    private final ApiClient apiClient;
    private final ApiAuthService remoteAuthService;
    private final ApiUserService remoteUserService;
    private final ApiDepartmentService remoteDepartmentService;
    private final ApiEquipmentService remoteEquipmentService;
    private final ApiAssignmentService remoteAssignmentService;
    private final ApiDistributionService remoteDistributionService;
    private final ApiReturnService remoteReturnService;
    private final ApiReportService remoteReportService;
    private final ApiAssetHistoryService remoteAssetHistoryService;
    private final ApiDataMaintenanceService remoteDataMaintenanceService;
    private final LocalMirrorRepository localMirrorRepository = new LocalMirrorRepository();
    private final HttpClient probeClient = HttpClient.newBuilder().connectTimeout(PROBE_TIMEOUT).build();
    private volatile long lastProbeAt;
    private volatile boolean lastProbeResult;

    public RemoteMirrorCoordinator(AppConfiguration configuration, ApiClient apiClient) {
        this.configuration = configuration;
        this.apiClient = apiClient;
        this.remoteAuthService = apiClient == null ? null : new ApiAuthService(apiClient);
        this.remoteUserService = apiClient == null ? null : new ApiUserService(apiClient);
        this.remoteDepartmentService = apiClient == null ? null : new ApiDepartmentService(apiClient);
        this.remoteEquipmentService = apiClient == null ? null : new ApiEquipmentService(apiClient);
        this.remoteAssignmentService = apiClient == null ? null : new ApiAssignmentService(apiClient);
        this.remoteDistributionService = apiClient == null ? null : new ApiDistributionService(apiClient);
        this.remoteReturnService = apiClient == null ? null : new ApiReturnService(apiClient);
        this.remoteReportService = apiClient == null ? null : new ApiReportService(apiClient);
        this.remoteAssetHistoryService = apiClient == null ? null : new ApiAssetHistoryService(apiClient);
        this.remoteDataMaintenanceService = apiClient == null ? null : new ApiDataMaintenanceService(apiClient);
    }

    public boolean isMirrorConfigured() {
        return apiClient != null
                && configuration.getApiBaseUrl() != null
                && !configuration.getApiBaseUrl().isBlank();
    }

    public boolean canAttemptRemoteAuthentication() {
        return isMirrorConfigured() && isApiReachable();
    }

    public boolean hasRemoteSession() {
        return isMirrorConfigured()
                && Session.getAuthToken() != null
                && !Session.getAuthToken().isBlank()
                && isApiReachable();
    }

    public ApiAuthService getRemoteAuthService() {
        return remoteAuthService;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public ApiUserService getRemoteUserService() {
        return remoteUserService;
    }

    public ApiDepartmentService getRemoteDepartmentService() {
        return remoteDepartmentService;
    }

    public ApiEquipmentService getRemoteEquipmentService() {
        return remoteEquipmentService;
    }

    public ApiAssignmentService getRemoteAssignmentService() {
        return remoteAssignmentService;
    }

    public ApiDistributionService getRemoteDistributionService() {
        return remoteDistributionService;
    }

    public ApiReturnService getRemoteReturnService() {
        return remoteReturnService;
    }

    public ApiReportService getRemoteReportService() {
        return remoteReportService;
    }

    public ApiAssetHistoryService getRemoteAssetHistoryService() {
        return remoteAssetHistoryService;
    }

    public ApiDataMaintenanceService getRemoteDataMaintenanceService() {
        return remoteDataMaintenanceService;
    }

    public void handleRemoteLogin(User user, String plainPassword) throws Exception {
        Map<String, String> passwordOverrides = new HashMap<>();
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()
                && plainPassword != null && !plainPassword.isBlank()) {
            passwordOverrides.put(user.getEmail().trim().toLowerCase(), PasswordUtils.hash(plainPassword));
        }
        synchronizeFromRemote(passwordOverrides);
        if (user != null && !passwordOverrides.isEmpty()) {
            localMirrorRepository.upsertAuthenticatedUser(user, passwordOverrides.get(user.getEmail().trim().toLowerCase()));
        }
    }

    public void updateMirroredPassword(String identifier, String plainPassword) throws Exception {
        if (identifier == null || identifier.isBlank() || plainPassword == null || plainPassword.isBlank()) {
            return;
        }
        localMirrorRepository.updateMirroredPassword(identifier, PasswordUtils.hash(plainPassword));
    }

    public void synchronizeQuietlyIfOnline() {
        if (!hasRemoteSession()) {
            return;
        }
        try {
            synchronizeFromRemote(Map.of());
        } catch (Exception ignored) {
            // Keep local workflows available if the mirror refresh fails mid-session.
        }
    }

    public void refreshAfterRemoteMutation() {
        if (AUTO_MIRROR_SUPPRESSED.get()) {
            return;
        }
        if (!configuration.isAutoMirrorAfterMutationEnabled()) {
            return;
        }
        synchronizeQuietlyIfOnline();
    }

    public static void runWithAutoMirrorSuppressed(CheckedRunnable runnable) throws Exception {
        boolean previous = AUTO_MIRROR_SUPPRESSED.get();
        AUTO_MIRROR_SUPPRESSED.set(true);
        try {
            runnable.run();
        } finally {
            AUTO_MIRROR_SUPPRESSED.set(previous);
        }
    }

    public void synchronizeFromRemote(Map<String, String> passwordOverridesByEmail) throws Exception {
        if (!hasRemoteSession()) {
            return;
        }

        List<String> departments = remoteDepartmentService.getDepartments();
        List<User> users = remoteUserService.getUsers();
        List<Equipment> equipment = remoteReportService.getInventoryReport();
        List<Assignment> assignments = remoteReportService.getAssignmentReport();
        List<Distribution> distributions = remoteReportService.getDistributionReport();
        List<ReturnRecord> returns = remoteReportService.getReturnReport();
        List<AuditLog> auditLogs = fetchAuditLogs();

        localMirrorRepository.synchronizeFromRemote(
                departments,
                users,
                normalizePasswordOverrides(passwordOverridesByEmail),
                equipment,
                assignments,
                distributions,
                returns,
                auditLogs
        );
    }

    private List<AuditLog> fetchAuditLogs() throws Exception {
        AuditLogPayload[] payloads = apiClient.get("/api/audit-logs", AuditLogPayload[].class);
        if (payloads == null || payloads.length == 0) {
            return List.of();
        }
        return java.util.Arrays.stream(payloads)
                .map(payload -> new AuditLog(
                        payload.id,
                        payload.username,
                        payload.action,
                        payload.moduleName,
                        payload.details,
                        payload.actionTime
                ))
                .collect(Collectors.toList());
    }

    private Map<String, String> normalizePasswordOverrides(Map<String, String> passwordOverridesByEmail) {
        Map<String, String> normalized = new HashMap<>();
        if (passwordOverridesByEmail == null) {
            return normalized;
        }
        for (Map.Entry<String, String> entry : passwordOverridesByEmail.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            normalized.put(entry.getKey().trim().toLowerCase(), entry.getValue().trim());
        }
        return normalized;
    }

    private boolean isApiReachable() {
        long now = System.currentTimeMillis();
        if (now - lastProbeAt < PROBE_CACHE_TTL_MS) {
            return lastProbeResult;
        }
        boolean reachable = isReachable("/actuator/health") || isReachable("/");
        lastProbeResult = reachable;
        lastProbeAt = now;
        return reachable;
    }

    private boolean isReachable(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(joinPath(configuration.getApiBaseUrl(), path)))
                    .timeout(PROBE_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<Void> response = probeClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String joinPath(String baseUrl, String path) {
        String normalizedBase = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        return normalizedBase + path;
    }

    private static final class AuditLogPayload {
        public int id;
        public String username;
        public String action;
        public String moduleName;
        public String details;
        public String actionTime;
    }

    @FunctionalInterface
    public interface CheckedRunnable {
        void run() throws Exception;
    }
}
