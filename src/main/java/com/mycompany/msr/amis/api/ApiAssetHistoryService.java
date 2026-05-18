package com.mycompany.msr.amis;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class ApiAssetHistoryService implements AssetHistoryService {

    private final ApiClient apiClient;

    public ApiAssetHistoryService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public AssetHistoryResult getAssetHistory(String assetCode) {
        try {
            AssetHistoryPayload payload = apiClient.get("/api/assets/" + assetCode + "/history", AssetHistoryPayload.class);
            if (payload == null || payload.summary == null) {
                return null;
            }
            List<AssetHistoryRecord> records = Arrays.stream(payload.records == null ? new HistoryRecordPayload[0] : payload.records)
                    .map(item -> new AssetHistoryRecord(
                            item.activityDate,
                            item.eventType,
                            item.actor,
                            item.affectedPerson,
                            item.details,
                            item.status
                    ))
                    .collect(Collectors.toList());
            return new AssetHistoryResult(
                    new AssetHistorySummary(
                            payload.summary.assetCode,
                            payload.summary.serialNumber,
                            payload.summary.equipmentName,
                            payload.summary.category,
                            payload.summary.entryDate,
                            payload.summary.currentStatus
                    ),
                    records
            );
        } catch (ApiClientException exception) {
            if (exception.getStatusCode() == 404) {
                return null;
            }
            throw new IllegalStateException(resolveMessage(exception), exception);
        } catch (Exception exception) {
            throw new IllegalStateException(resolveMessage(exception), exception);
        }
    }

    private String resolveMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Failed to load asset history."
                : exception.getMessage();
    }

    private static final class AssetHistoryPayload {
        public SummaryPayload summary;
        public HistoryRecordPayload[] records;
    }

    private static final class SummaryPayload {
        public String assetCode;
        public String serialNumber;
        public String equipmentName;
        public String category;
        public String entryDate;
        public String currentStatus;
    }

    private static final class HistoryRecordPayload {
        public String activityDate;
        public String eventType;
        public String actor;
        public String affectedPerson;
        public String details;
        public String status;
    }
}
