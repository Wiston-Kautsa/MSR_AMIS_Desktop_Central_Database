package com.mycompany.msr.amis;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ApiAssetHistoryService implements AssetHistoryService {

    private final ApiClient apiClient;
    private final LocalAssetHistoryService localAssetHistoryService = new LocalAssetHistoryService();

    public ApiAssetHistoryService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public AssetHistoryResult getAssetHistory(String assetCode) {
        AssetHistoryResult localResult = loadLocalHistory(assetCode);
        try {
            AssetHistoryPayload payload = apiClient.get("/api/assets/" + assetCode + "/history", AssetHistoryPayload.class);
            if (payload == null || payload.summary == null) {
                return localResult;
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
            AssetHistorySummary apiSummary = new AssetHistorySummary(
                            payload.summary.assetCode,
                            payload.summary.serialNumber,
                            payload.summary.equipmentName,
                            payload.summary.category,
                            payload.summary.entryDate,
                            payload.summary.currentStatus
                    );
            return merge(apiSummary, records, localResult);
        } catch (ApiClientException exception) {
            if (exception.getStatusCode() == 404) {
                return localResult;
            }
            throw new IllegalStateException(resolveMessage(exception), exception);
        } catch (Exception exception) {
            if (localResult != null) {
                return localResult;
            }
            throw new IllegalStateException(resolveMessage(exception), exception);
        }
    }

    private AssetHistoryResult loadLocalHistory(String assetCode) {
        try {
            return localAssetHistoryService.getAssetHistory(assetCode);
        } catch (Exception ignored) {
            return null;
        }
    }

    private AssetHistoryResult merge(AssetHistorySummary apiSummary,
                                     List<AssetHistoryRecord> apiRecords,
                                     AssetHistoryResult localResult) {
        AssetHistorySummary summary = localResult != null && localResult.getSummary() != null
                ? localResult.getSummary()
                : apiSummary;

        Map<String, AssetHistoryRecord> merged = new LinkedHashMap<>();
        addRecords(merged, apiRecords);
        if (localResult != null) {
            addRecords(merged, localResult.getRecords());
        }

        List<AssetHistoryRecord> records = new ArrayList<>(merged.values());
        records.sort(Comparator
                .comparing(AssetHistoryRecord::getActivityDate, Comparator.nullsLast(String::compareTo))
                .reversed()
                .thenComparing(AssetHistoryRecord::getEventType, Comparator.nullsLast(String::compareTo)));
        return new AssetHistoryResult(summary, records);
    }

    private void addRecords(Map<String, AssetHistoryRecord> merged, List<AssetHistoryRecord> records) {
        if (records == null) {
            return;
        }
        for (AssetHistoryRecord record : records) {
            merged.putIfAbsent(historyKey(record), record);
        }
    }

    private String historyKey(AssetHistoryRecord record) {
        if (record == null) {
            return "";
        }
        return normalize(record.getActivityDate()) + "|"
                + normalize(record.getEventType()) + "|"
                + normalize(record.getActor()) + "|"
                + normalize(record.getAffectedPerson()) + "|"
                + normalize(record.getDetails()) + "|"
                + normalize(record.getStatus());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
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
