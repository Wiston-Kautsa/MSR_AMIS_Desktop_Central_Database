package com.mycompany.msr.amis;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class ApiDistributionService implements DistributionService {

    private final ApiClient apiClient;

    public ApiDistributionService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public List<String> getAvailableEquipment() {
        try {
            String[] payload = apiClient.get("/api/distributions/available-equipment", String[].class);
            return Arrays.asList(payload == null ? new String[0] : payload);
        } catch (Exception e) {
            throw new IllegalStateException(resolveMessage(e), e);
        }
    }

    @Override
    public List<String> getAvailableEquipmentByCategory(String category) {
        try {
            String[] payload = apiClient.get("/api/distributions/available-equipment?category=" + java.net.URLEncoder.encode(category, java.nio.charset.StandardCharsets.UTF_8), String[].class);
            return Arrays.asList(payload == null ? new String[0] : payload);
        } catch (Exception e) {
            throw new IllegalStateException(resolveMessage(e), e);
        }
    }

    @Override
    public List<Distribution> getCurrentDistributions() {
        try {
            DistributionPayload[] payloads = apiClient.get("/api/distributions/current", DistributionPayload[].class);
            return Arrays.stream(payloads == null ? new DistributionPayload[0] : payloads)
                    .map(DistributionPayload::toDistribution)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IllegalStateException(resolveMessage(e), e);
        }
    }

    @Override
    public Distribution getCurrentDistributionForAsset(String assetCode) {
        try {
            DistributionPayload payload = apiClient.get("/api/distributions/asset/" + assetCode, DistributionPayload.class);
            return payload == null ? null : payload.toDistribution();
        } catch (ApiClientException e) {
            if (e.getStatusCode() == 404) {
                return null;
            }
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(resolveMessage(e), e);
        }
    }

    @Override
    public void distributeEquipmentBatch(int assignmentId, List<Distribution> distributions) throws Exception {
        apiClient.post("/api/distributions/batch", DistributionBatchRequest.from(assignmentId, distributions), Void.class);
        ServiceRegistry.getRemoteMirrorCoordinator().refreshAfterRemoteMutation();
    }

    private String resolveMessage(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank() ? "Failed to call the distribution API." : e.getMessage();
    }

    private static final class DistributionBatchRequest {
        public int assignmentId;
        public List<DistributionRequest> distributions;

        static DistributionBatchRequest from(int assignmentId, List<Distribution> values) {
            DistributionBatchRequest request = new DistributionBatchRequest();
            request.assignmentId = assignmentId;
            request.distributions = values.stream().map(DistributionRequest::from).collect(Collectors.toList());
            return request;
        }
    }

    private static final class DistributionRequest {
        public String assetCode;
        public String assignedTo;
        public String phone;
        public String nid;

        static DistributionRequest from(Distribution distribution) {
            DistributionRequest request = new DistributionRequest();
            request.assetCode = distribution.getAssetCode();
            request.assignedTo = distribution.getAssignedTo();
            request.phone = distribution.getPhone();
            request.nid = distribution.getNid();
            return request;
        }
    }

    private static final class DistributionPayload {
        public int id;
        public String assetCode;
        public String assignedTo;
        public String phone;
        public String nid;
        public String date;

        private Distribution toDistribution() {
            return new Distribution(id, assetCode, "", assignedTo, phone, nid, java.time.LocalDate.parse(date));
        }
    }
}
