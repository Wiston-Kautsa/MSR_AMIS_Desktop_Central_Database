package com.mycompany.msr.amis.api.service;

import com.mycompany.msr.amis.api.dto.assignment.AssignmentRequest;
import com.mycompany.msr.amis.api.dto.assignment.AssignmentResponse;
import com.mycompany.msr.amis.api.dto.distribution.DistributionBatchRequest;
import com.mycompany.msr.amis.api.dto.distribution.DistributionRequest;
import com.mycompany.msr.amis.api.dto.distribution.DistributionResponse;
import com.mycompany.msr.amis.api.dto.returns.ReturnBatchRequest;
import com.mycompany.msr.amis.api.dto.returns.ReturnBatchResponse;
import com.mycompany.msr.amis.api.dto.returns.ReturnItemRequest;
import com.mycompany.msr.amis.api.dto.returns.ReturnResponse;
import com.mycompany.msr.amis.api.exception.ApiException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsService {

    private final JdbcTemplate jdbcTemplate;
    private final ActionAuditService actionAuditService;
    private final OperationalNotificationEmailService operationalNotificationEmailService;

    public OperationsService(JdbcTemplate jdbcTemplate,
                             ActionAuditService actionAuditService,
                             OperationalNotificationEmailService operationalNotificationEmailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.actionAuditService = actionAuditService;
        this.operationalNotificationEmailService = operationalNotificationEmailService;
    }

    public List<AssignmentResponse> getAssignments() {
        return jdbcTemplate.query(
                "SELECT id, person, department, equipment_type, reason, quantity, date_created, COALESCE(status, 'ACTIVE') AS lifecycle_status FROM assignments ORDER BY date_created DESC, id DESC",
                (rs, rowNum) -> mapAssignment(
                        rs.getInt("id"),
                        rs.getString("person"),
                        rs.getString("department"),
                        rs.getString("equipment_type"),
                        rs.getString("reason"),
                        rs.getInt("quantity"),
                        rs.getDate("date_created").toLocalDate().toString(),
                        rs.getString("lifecycle_status")
                )
        );
    }

    public List<AssignmentResponse> getAssignmentsPendingReturn() {
        return jdbcTemplate.query(
                "SELECT DISTINCT a.id, a.person, a.department, a.equipment_type, a.reason, a.quantity, a.date_created, COALESCE(a.status, 'ACTIVE') AS lifecycle_status " +
                        "FROM assignments a INNER JOIN distribution d ON d.assignment_id = a.id " +
                        "WHERE d.returned = FALSE ORDER BY a.date_created DESC, a.id DESC",
                (rs, rowNum) -> mapAssignment(
                        rs.getInt("id"),
                        rs.getString("person"),
                        rs.getString("department"),
                        rs.getString("equipment_type"),
                        rs.getString("reason"),
                        rs.getInt("quantity"),
                        rs.getDate("date_created").toLocalDate().toString(),
                        rs.getString("lifecycle_status")
                )
        );
    }

    public Map<String, Integer> getAvailableStockByCategory() {
        Map<String, Integer> result = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT TRIM(category) AS category, COUNT(*) AS total " +
                        "FROM equipment e WHERE e.status = 'AVAILABLE' AND e.asset_code NOT IN (SELECT asset_code FROM distribution WHERE returned = FALSE) " +
                        "GROUP BY TRIM(category) ORDER BY TRIM(category)"
        );
        for (Map<String, Object> row : rows) {
            result.put((String) row.get("category"), ((Number) row.get("total")).intValue());
        }
        return result;
    }

    public int getAvailableStock(String equipmentType) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM equipment e WHERE TRIM(e.category)=TRIM(?) " +
                        "AND e.status = 'AVAILABLE' " +
                        "AND e.asset_code NOT IN (SELECT asset_code FROM distribution WHERE returned = FALSE)",
                Integer.class,
                equipmentType
        );
        return count == null ? 0 : count;
    }

    public int getDistributedCountForAssignment(int assignmentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM distribution WHERE assignment_id = ? AND returned = FALSE",
                Integer.class,
                assignmentId
        );
        return count == null ? 0 : count;
    }

    @Transactional
    public AssignmentResponse createAssignment(String actor, AssignmentRequest request) {
        validateAssignmentRequest(request);
        if (request.quantity() > getAvailableStock(request.equipmentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Requested quantity exceeds available stock.");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO assignments(person, department, equipment_type, reason, quantity, status, date_created) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    new String[]{"id"}
            );
            ps.setString(1, request.person().trim());
            ps.setString(2, request.department().trim());
            ps.setString(3, request.equipmentType().trim());
            ps.setString(4, request.reason().trim());
            ps.setInt(5, request.quantity());
            ps.setString(6, "ACTIVE");
            ps.setDate(7, Date.valueOf(LocalDate.now()));
            return ps;
        }, keyHolder);

        Number id = keyHolder.getKey();
        if (id == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create assignment.");
        }
        AssignmentResponse response = getAssignmentById(id.intValue());
        actionAuditService.log(
                actor,
                "CREATE_ASSIGNMENT",
                "ASSIGNMENTS",
                Integer.toString(response.id()),
                "Assignment created for " + response.person() + ". Department: " + response.department() +
                        ", equipment: " + response.equipmentType() + ", quantity: " + response.quantity() +
                        ", reason: " + response.reason()
        );
        return response;
    }

    @Transactional
    public AssignmentResponse updateAssignment(String actor, int id, AssignmentRequest request) {
        validateAssignmentRequest(request);
        assertAssignmentMutable(id);
        if (getDistributedCountForAssignment(id) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This assignment already has distributed equipment and cannot be edited.");
        }
        if (request.quantity() > getAvailableStock(request.equipmentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Requested quantity exceeds available stock.");
        }

        AssignmentResponse oldAssignment = getAssignmentById(id);
        int updated = jdbcTemplate.update(
                "UPDATE assignments SET person=?, department=?, equipment_type=?, reason=?, quantity=? WHERE id=?",
                request.person().trim(), request.department().trim(), request.equipmentType().trim(),
                request.reason().trim(), request.quantity(), id
        );
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Assignment not found.");
        }
        AssignmentResponse response = getAssignmentById(id);
        actionAuditService.log(
                actor,
                "EDIT_ASSIGNMENT",
                "ASSIGNMENTS",
                Integer.toString(id),
                "Assignment edited. Old: " + assignmentSnapshot(oldAssignment) + ". New: " + assignmentSnapshot(response)
        );
        return response;
    }

    @Transactional
    public void deleteAssignment(String actor, int id) {
        assertAssignmentMutable(id);
        AssignmentResponse oldAssignment = getAssignmentById(id);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM distribution WHERE assignment_id = ?", Integer.class, id);
        if (count != null && count > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Assignments that already have distributed equipment cannot be deleted.");
        }
        int deleted = jdbcTemplate.update("DELETE FROM assignments WHERE id = ?", id);
        if (deleted == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Assignment not found.");
        }
        actionAuditService.log(actor, "DELETE_ASSIGNMENT", "ASSIGNMENTS", Integer.toString(id), "Assignment deleted. Old: " + assignmentSnapshot(oldAssignment));
    }

    @Transactional
    public AssignmentResponse updateAssignmentStatus(String actor, int id, String rawStatus) {
        String nextStatus = parseAssignmentStatus(rawStatus);
        int updated = jdbcTemplate.update("UPDATE assignments SET status = ? WHERE id = ?", nextStatus, id);
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Assignment not found.");
        }
        actionAuditService.log(
                actor,
                "ASSIGNMENT_" + nextStatus,
                "ASSIGNMENTS",
                Integer.toString(id),
                "Assignment status changed to " + nextStatus
        );
        return getAssignmentById(id);
    }

    public List<String> getAvailableEquipment(String category) {
        if (category == null || category.isBlank()) {
            return jdbcTemplate.queryForList(
                    "SELECT asset_code FROM equipment WHERE status = 'AVAILABLE' AND asset_code NOT IN (SELECT asset_code FROM distribution WHERE returned = FALSE) ORDER BY asset_code",
                    String.class
            );
        }
        return jdbcTemplate.queryForList(
                "SELECT asset_code FROM equipment WHERE TRIM(category)=TRIM(?) " +
                        "AND status = 'AVAILABLE' " +
                        "AND asset_code NOT IN (SELECT asset_code FROM distribution WHERE returned = FALSE) ORDER BY asset_code",
                String.class,
                category.trim()
        );
    }

    public List<DistributionResponse> getCurrentDistributions() {
        return jdbcTemplate.query(
                "SELECT id, asset_code, assigned_to, phone, nid, assigned_at FROM distribution WHERE returned = FALSE ORDER BY assigned_at DESC, id DESC",
                (rs, rowNum) -> new DistributionResponse(
                        rs.getInt("id"),
                        rs.getString("asset_code"),
                        rs.getString("assigned_to"),
                        rs.getString("phone"),
                        rs.getString("nid"),
                        rs.getDate("assigned_at").toLocalDate().toString()
                )
        );
    }

    public DistributionResponse getCurrentDistributionForAsset(String assetCode) {
        List<DistributionResponse> results = jdbcTemplate.query(
                "SELECT id, asset_code, assigned_to, phone, nid, assigned_at FROM distribution " +
                        "WHERE asset_code = ? AND returned = FALSE ORDER BY assigned_at DESC, id DESC LIMIT 1",
                (rs, rowNum) -> new DistributionResponse(
                        rs.getInt("id"),
                        rs.getString("asset_code"),
                        rs.getString("assigned_to"),
                        rs.getString("phone"),
                        rs.getString("nid"),
                        rs.getDate("assigned_at").toLocalDate().toString()
                ),
                assetCode
        );
        return results.isEmpty() ? null : results.get(0);
    }

    @Transactional
    public void distributeBatch(String actor, DistributionBatchRequest request) {
        List<String> distributedAssetCodes = new ArrayList<>();
        for (var distribution : request.distributions()) {
            validateDistribution(request.assignmentId(), distribution);
            jdbcTemplate.update(
                    "INSERT INTO distribution(asset_code, assignment_id, assigned_to, phone, nid, assigned_at, returned) VALUES (?, ?, ?, ?, ?, ?, FALSE)",
                    distribution.assetCode().trim(), request.assignmentId(), distribution.assignedTo().trim(),
                    distribution.phone().trim(), distribution.nid().trim(), Date.valueOf(LocalDate.now())
            );
            jdbcTemplate.update("UPDATE equipment SET status = 'ASSIGNED' WHERE asset_code = ?", distribution.assetCode().trim());
            distributedAssetCodes.add(distribution.assetCode().trim());
        }
        actionAuditService.log(
                actor,
                "DISTRIBUTE_EQUIPMENT_BATCH",
                "DISTRIBUTION",
                Integer.toString(request.assignmentId()),
                "Distributed " + distributedAssetCodes.size() + " equipment item(s) under assignment " +
                        request.assignmentId() + ": " + String.join(", ", distributedAssetCodes)
        );
        operationalNotificationEmailService.notifyDistribution(actor, request.assignmentId(), distributedAssetCodes);
    }

    public List<String> getOutstandingAssetCodes(int assignmentId) {
        return jdbcTemplate.queryForList(
                "SELECT asset_code FROM distribution WHERE assignment_id = ? AND returned = FALSE ORDER BY assigned_at DESC, id DESC",
                String.class,
                assignmentId
        );
    }

    public List<ReturnResponse> getReturns() {
        return jdbcTemplate.query(
                "SELECT asset_code, returned_by, phone, nid, item_condition, return_date FROM returns ORDER BY return_date DESC, id DESC",
                (rs, rowNum) -> new ReturnResponse(
                        rs.getString("asset_code"),
                        rs.getString("returned_by"),
                        rs.getString("phone"),
                        rs.getString("nid"),
                        rs.getString("item_condition"),
                        rs.getDate("return_date").toLocalDate().toString()
                )
        );
    }

    @Transactional
    public ReturnBatchResponse completeReturns(String actor, ReturnBatchRequest request) {
        List<String> outstandingAssets = new ArrayList<>(getOutstandingAssetCodes(request.assignmentId()));
        List<String> replacementAssetCodes = new ArrayList<>();
        List<String> returnedAssetCodes = new ArrayList<>();

        for (ReturnItemRequest item : request.items()) {
            if (!removeOutstandingAsset(outstandingAssets, item.originalAssetCode())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Asset is not outstanding for the selected assignment or was already returned: " + item.originalAssetCode()
                );
            }
            String remarks = normalize(item.remarks());
            String previousCondition = getEquipmentCondition(item.originalAssetCode());

            if (item.replacement()) {
                String replacementAssetCode = insertReplacementEquipment(request.equipmentType(), item.enteredIdentifier(), "Replacement return for " + item.originalAssetCode());
                replacementAssetCodes.add(replacementAssetCode);
                remarks = appendRemark(remarks, "Replacement equipment recorded as " + replacementAssetCode +
                        " using IMEI/Serial " + item.enteredIdentifier());
            }

            int updated = jdbcTemplate.update("UPDATE distribution SET returned = TRUE WHERE asset_code = ? AND returned = FALSE", item.originalAssetCode());
            if (updated == 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Asset not found or already returned: " + item.originalAssetCode());
            }

            jdbcTemplate.update(
                    "INSERT INTO returns(asset_code, returned_by, phone, nid, item_condition, previous_condition, remarks, return_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    item.originalAssetCode(), item.returnedBy().trim(), item.phone().trim(), item.nid().trim(),
                    item.condition().trim(), previousCondition, remarks, Date.valueOf(LocalDate.now())
            );
            jdbcTemplate.update("UPDATE equipment SET status = 'AVAILABLE', item_condition = ? WHERE asset_code = ?",
                    item.condition().trim(), item.originalAssetCode());
            returnedAssetCodes.add(item.originalAssetCode());
            actionAuditService.log(
                    actor,
                    "RETURN_EQUIPMENT",
                    "RETURNS",
                    item.originalAssetCode(),
                    "Equipment returned by " + item.returnedBy().trim() + ". Condition: " + item.condition().trim() +
                            (previousCondition.isBlank() ? "" : " (previously: " + previousCondition + ")") +
                            ", phone: " + item.phone().trim() + ", NID: " + item.nid().trim() +
                            (remarks.isBlank() ? "" : ", remarks: " + remarks)
            );
        }

        if (!outstandingAssets.isEmpty()) {
            for (String assetCode : outstandingAssets) {
                jdbcTemplate.update("UPDATE distribution SET outstanding_remarks = ? WHERE asset_code = ? AND returned = FALSE",
                        resolveOutstandingRemark(request, assetCode), assetCode);
            }
        }

        operationalNotificationEmailService.notifyReturns(actor, request.assignmentId(), returnedAssetCodes);
        return new ReturnBatchResponse(true, "Equipment returns saved successfully.", replacementAssetCodes);
    }

    private AssignmentResponse getAssignmentById(int id) {
        List<AssignmentResponse> results = jdbcTemplate.query(
                "SELECT id, person, department, equipment_type, reason, quantity, date_created, COALESCE(status, 'ACTIVE') AS lifecycle_status FROM assignments WHERE id = ?",
                (rs, rowNum) -> mapAssignment(
                        rs.getInt("id"),
                        rs.getString("person"),
                        rs.getString("department"),
                        rs.getString("equipment_type"),
                        rs.getString("reason"),
                        rs.getInt("quantity"),
                        rs.getDate("date_created").toLocalDate().toString(),
                        rs.getString("lifecycle_status")
                ),
                id
        );
        if (results.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Assignment not found.");
        }
        return results.get(0);
    }

    private AssignmentResponse mapAssignment(int id, String person, String department, String equipmentType, String reason, int quantity, String date, String lifecycleStatus) {
        int distributedCount = getDistributedCountForAssignment(id);
        String status = normalize(lifecycleStatus);
        if (status.isBlank() || "ACTIVE".equalsIgnoreCase(status)) {
            status = distributedCount == 0 ? "PENDING" : distributedCount < quantity ? "PARTIAL" : "COMPLETE";
        }
        return new AssignmentResponse(id, person, department, equipmentType, reason, quantity, date, distributedCount, status);
    }

    private String assignmentSnapshot(AssignmentResponse assignment) {
        if (assignment == null) {
            return "not found";
        }
        return "id=" + assignment.id() +
                ", person=" + normalize(assignment.person()) +
                ", department=" + normalize(assignment.department()) +
                ", equipment=" + normalize(assignment.equipmentType()) +
                ", quantity=" + assignment.quantity() +
                ", status=" + normalize(assignment.status()) +
                ", date=" + normalize(assignment.date()) +
                ", reason=" + normalize(assignment.reason());
    }

    private void validateAssignmentRequest(AssignmentRequest request) {
        if (normalize(request.person()).isBlank() || normalize(request.department()).isBlank()
                || normalize(request.equipmentType()).isBlank() || normalize(request.reason()).isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Person, department, equipment type, and reason are required.");
        }
        if (request.quantity() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Quantity must be greater than zero.");
        }
    }

    private void validateDistribution(int assignmentId, DistributionRequest distribution) {
        List<Map<String, Object>> assignments = jdbcTemplate.queryForList(
                "SELECT person, equipment_type, COALESCE(status, 'ACTIVE') AS status FROM assignments WHERE id = ?", assignmentId
        );
        if (assignments.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Assignment record was not found.");
        }
        Map<String, Object> assignment = assignments.get(0);
        String assignmentType = normalize((String) assignment.get("equipment_type"));
        String assignmentStatus = normalize((String) assignment.get("status"));
        if ("FROZEN".equalsIgnoreCase(assignmentStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Frozen assignments cannot be changed.");
        }
        if ("RETIRED".equalsIgnoreCase(assignmentStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Retired assignments are closed.");
        }

        List<Map<String, Object>> equipmentRows = jdbcTemplate.queryForList(
                "SELECT category, status FROM equipment WHERE asset_code = ?", distribution.assetCode().trim()
        );
        if (equipmentRows.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Selected equipment does not exist: " + distribution.assetCode());
        }
        Map<String, Object> equipment = equipmentRows.get(0);
        String equipmentCategory = normalize((String) equipment.get("category"));
        String equipmentStatus = normalize((String) equipment.get("status"));

        if (!assignmentType.equalsIgnoreCase(equipmentCategory)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Equipment " + distribution.assetCode() + " is " + equipmentCategory +
                    " but the assignment requires " + assignmentType + ".");
        }
        if (!"AVAILABLE".equalsIgnoreCase(equipmentStatus)) {
            if ("RETIRED".equalsIgnoreCase(equipmentStatus)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Retired equipment cannot be assigned: " + distribution.assetCode());
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, "Equipment is already assigned: " + distribution.assetCode());
        }
    }

    private void assertAssignmentMutable(int id) {
        String status = jdbcTemplate.query(
                "SELECT COALESCE(status, 'ACTIVE') FROM assignments WHERE id = ?",
                rs -> rs.next() ? normalize(rs.getString(1)) : "",
                id
        );
        if (status.isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Assignment not found.");
        }
        if ("FROZEN".equalsIgnoreCase(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Frozen assignments cannot be changed.");
        }
        if ("RETIRED".equalsIgnoreCase(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Retired assignments are closed.");
        }
    }

    private String parseAssignmentStatus(String rawStatus) {
        String normalized = normalize(rawStatus).toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(normalized) && !"FROZEN".equals(normalized) && !"RETIRED".equals(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid assignment status.");
        }
        return normalized;
    }

    private String insertReplacementEquipment(String equipmentType, String serialNumber, String source) {
        String normalizedType = normalize(equipmentType);
        String normalizedSerialNumber = normalize(serialNumber);
        if (equipmentIdentifierExists(normalizedSerialNumber)) {
            throw new ApiException(HttpStatus.CONFLICT, "Asset code or serial number already exists: " + normalizedSerialNumber);
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO equipment(asset_code, name, category, serial_number, item_condition, source, entry_date, status, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    new String[]{"id"}
            );
            ps.setString(1, "TMP-" + normalizedType.toUpperCase(Locale.ROOT).replace(" ", "") + "-" + System.nanoTime());
            ps.setString(2, normalizedType);
            ps.setString(3, normalizedType);
            ps.setString(4, normalizedSerialNumber);
            ps.setString(5, "New");
            ps.setString(6, normalize(source));
            ps.setDate(7, Date.valueOf(LocalDate.now()));
            return ps;
        }, keyHolder);

        Number id = keyHolder.getKey();
        if (id == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Replacement equipment was saved but asset code could not be resolved.");
        }

        String assetCode = EquipmentFacadeService.generateAssetCode(normalizedType, id.longValue());
        jdbcTemplate.update("UPDATE equipment SET asset_code = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", assetCode, id.longValue());
        return assetCode;
    }

    private boolean equipmentIdentifierExists(String identifier) {
        String normalizedIdentifier = normalize(identifier);
        if (normalizedIdentifier.isBlank()) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM equipment " +
                        "WHERE LOWER(TRIM(asset_code)) = LOWER(TRIM(?)) " +
                        "OR LOWER(TRIM(serial_number)) = LOWER(TRIM(?))",
                Integer.class,
                normalizedIdentifier,
                normalizedIdentifier
        );
        return count != null && count > 0;
    }

    private String getEquipmentCondition(String assetCode) {
        List<String> conditions = jdbcTemplate.query(
                "SELECT COALESCE(item_condition, '') FROM equipment WHERE asset_code = ?",
                (rs, rowNum) -> normalize(rs.getString(1)),
                assetCode
        );
        return conditions.isEmpty() ? "" : conditions.get(0);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean removeOutstandingAsset(List<String> outstandingAssets, String assetCode) {
        for (int i = 0; i < outstandingAssets.size(); i++) {
            String outstandingAsset = outstandingAssets.get(i);
            if (outstandingAsset != null && assetCode != null && outstandingAsset.trim().equalsIgnoreCase(assetCode.trim())) {
                outstandingAssets.remove(i);
                return true;
            }
        }
        return false;
    }

    private String resolveOutstandingRemark(ReturnBatchRequest request, String assetCode) {
        Map<String, String> outstandingRemarks = request.outstandingRemarks();
        if (outstandingRemarks != null) {
            String directRemark = outstandingRemarks.get(assetCode);
            if (directRemark != null) {
                return normalize(directRemark);
            }
            for (Map.Entry<String, String> entry : outstandingRemarks.entrySet()) {
                if (entry.getKey() != null && entry.getKey().trim().equalsIgnoreCase(assetCode)) {
                    return normalize(entry.getValue());
                }
            }
        }
        return normalize(request.outstandingRemark());
    }

    private String appendRemark(String existing, String extra) {
        if (existing == null || existing.isBlank()) {
            return extra;
        }
        return existing + " | " + extra;
    }
}
