package com.mycompany.msr.amis;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ApiReportService implements ReportService {

    private final ApiClient apiClient;

    public ApiReportService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public List<Equipment> getInventoryReport() {
        try {
            InventoryPayload[] payloads = apiClient.get("/api/reports/inventory", InventoryPayload[].class);
            return Arrays.stream(payloads == null ? new InventoryPayload[0] : payloads)
                    .map(InventoryPayload::toEquipment)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IllegalStateException(resolveMessage(e), e);
        }
    }

    @Override
    public List<Assignment> getAssignmentReport() {
        try {
            AssignmentPayload[] payloads = apiClient.get("/api/reports/assignments", AssignmentPayload[].class);
            return Arrays.stream(payloads == null ? new AssignmentPayload[0] : payloads)
                    .map(AssignmentPayload::toAssignment)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IllegalStateException(resolveMessage(e), e);
        }
    }

    @Override
    public List<Distribution> getDistributionReport() {
        try {
            Map<String, String> localRemarks = loadLocalOutstandingRemarks();
            DistributionPayload[] payloads = apiClient.get("/api/reports/distributions", DistributionPayload[].class);
            return Arrays.stream(payloads == null ? new DistributionPayload[0] : payloads)
                    .map(DistributionPayload::toDistribution)
                    .peek(distribution -> applyLocalOutstandingRemark(distribution, localRemarks))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IllegalStateException(resolveMessage(e), e);
        }
    }

    @Override
    public List<ReturnRecord> getReturnReport() {
        try {
            ReturnRecordPayload[] payloads = apiClient.get("/api/reports/returns", ReturnRecordPayload[].class);
            return Arrays.stream(payloads == null ? new ReturnRecordPayload[0] : payloads)
                    .map(ReturnRecordPayload::toRecord)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IllegalStateException(resolveMessage(e), e);
        }
    }

    @Override
    public List<Distribution> getOutstandingReport() {
        try {
            Map<String, String> localRemarks = loadLocalOutstandingRemarks();
            DistributionPayload[] payloads = apiClient.get("/api/reports/outstanding", DistributionPayload[].class);
            return Arrays.stream(payloads == null ? new DistributionPayload[0] : payloads)
                    .map(DistributionPayload::toDistribution)
                    .peek(distribution -> applyLocalOutstandingRemark(distribution, localRemarks))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IllegalStateException(resolveMessage(e), e);
        }
    }

    private Map<String, String> loadLocalOutstandingRemarks() {
        Map<String, String> remarksByAsset = new HashMap<>();
        String sql =
                "SELECT asset_code, outstanding_remarks " +
                "FROM distribution " +
                "WHERE returned = 0 " +
                "AND outstanding_remarks IS NOT NULL " +
                "AND TRIM(outstanding_remarks) <> ''";
        try (Connection connection = DatabaseHandler.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String assetCode = normalize(resultSet.getString("asset_code")).toLowerCase();
                String remarks = normalize(resultSet.getString("outstanding_remarks"));
                if (!assetCode.isBlank() && !remarks.isBlank()) {
                    remarksByAsset.put(assetCode, remarks);
                }
            }
        } catch (Exception ignored) {
            // API report data remains usable even when local cache is unavailable.
        }
        return remarksByAsset;
    }

    private void applyLocalOutstandingRemark(Distribution distribution, Map<String, String> localRemarks) {
        if (distribution == null || localRemarks == null || localRemarks.isEmpty()) {
            return;
        }
        if (normalize(distribution.getOutstandingRemarks()).isBlank()) {
            String localRemark = localRemarks.get(normalize(distribution.getAssetCode()).toLowerCase());
            if (localRemark != null && !localRemark.isBlank()) {
                distribution.setOutstandingRemarks(localRemark);
            }
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String resolveMessage(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank() ? "Failed to load report data." : e.getMessage();
    }

    public static final class InventoryPayload {
        public int id;
        public String assetCode;
        public String name;
        public String category;
        public String serialNumber;
        public String condition;
        public String source;
        public String entryDate;
        public String status;
        public String purchaseCost;
        public String location;
        public String warrantyExpiry;
        public String supplier;

        private Equipment toEquipment() {
            return new Equipment(
                    id,
                    assetCode,
                    serialNumber,
                    name,
                    category,
                    condition,
                    source,
                    entryDate,
                    status,
                    purchaseCost,
                    location,
                    warrantyExpiry,
                    supplier
            );
        }
    }

    public static final class AssignmentPayload {
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

    public static final class DistributionPayload {
        public int id;
        public String assetCode;
        public String responsiblePerson;
        public String assignedTo;
        public String phone;
        public String nid;
        public int assignmentId;
        public String date;
        public String status;
        public String outstandingRemarks;

        private Distribution toDistribution() {
            Distribution distribution = new Distribution(assetCode, "", assignedTo, phone, nid);
            distribution.setId(id);
            distribution.setResponsiblePerson(responsiblePerson);
            distribution.setAssignmentId(assignmentId);
            distribution.setDistributionDate(date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date));
            distribution.setStatus(status);
            distribution.setOutstandingRemarks(outstandingRemarks);
            return distribution;
        }
    }

    public static final class ReturnRecordPayload {
        public String assetCode;
        public String serialNumber;
        public String equipmentName;
        public String category;
        public String source;
        public String dateTaken;
        public String responsibleOfficer;
        public String assignmentEquipmentType;
        public String assignmentReason;
        public String returnedBy;
        public String phone;
        public String nid;
        public String returnCondition;
        public String remarks;
        public String returnDate;

        private ReturnRecord toRecord() {
            return new ReturnRecord(
                    assetCode,
                    serialNumber,
                    equipmentName,
                    category,
                    source,
                    dateTaken,
                    responsibleOfficer,
                    assignmentEquipmentType,
                    assignmentReason,
                    returnedBy,
                    phone,
                    nid,
                    returnCondition,
                    remarks,
                    returnDate
            );
        }
    }
}
