package com.mycompany.msr.amis.api.service;

import com.mycompany.msr.amis.api.dto.sync.SyncQueueRecordRequest;
import com.mycompany.msr.amis.api.dto.sync.SyncValidationIssueResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SyncValidationService {

    public List<SyncValidationIssueResponse> validateRecord(SyncQueueRecordRequest record) {
        List<SyncValidationIssueResponse> issues = new ArrayList<>();
        String entityType = normalize(record.entityType()).toUpperCase();
        String operation = normalize(record.operation()).toUpperCase();
        if (entityType.isBlank()) {
            issues.add(issue("ERROR", "MANDATORY_FIELD", "", record.entityId(), "Entity type is required."));
        }
        if (operation.isBlank()) {
            issues.add(issue("ERROR", "MANDATORY_FIELD", entityType, record.entityId(), "Operation is required."));
        }
        if (record.payload() == null || record.payload().isMissingNode() || record.payload().isNull()) {
            issues.add(issue("ERROR", "MANDATORY_FIELD", entityType, record.entityId(), "Payload is required."));
        }
        if ("EQUIPMENT".equals(entityType)) {
            if ("DELETE".equals(operation) || "STATUS".equals(operation)) {
                requireAnyPayload(record, issues, "Equipment asset code or serial number is required.", "assetCode", "serialNumber");
            } else {
                requirePayload(record, issues, "serialNumber", "Serial number is required.");
            }
        }
        if ("ASSIGNMENT".equals(entityType)) {
            if ("CREATE".equals(operation) || "UPDATE".equals(operation)) {
                requirePayload(record, issues, "person", "Assignment person is required.");
                requirePayload(record, issues, "department", "Assignment department is required.");
                requirePayload(record, issues, "equipmentType", "Assignment equipment type is required.");
                requirePayload(record, issues, "reason", "Assignment reason is required.");
                if (record.payload() != null && record.payload().path("quantity").asInt(0) <= 0) {
                    issues.add(issue("ERROR", "MANDATORY_FIELD", entityType, record.entityId(), "Assignment quantity must be greater than zero."));
                }
            }
        }
        if ("DISTRIBUTION".equals(entityType)) {
            requirePayloadArray(record, issues, "items", "Distribution items are required.");
        }
        if ("RETURN".equals(entityType)) {
            requirePayloadArray(record, issues, "items", "Return items are required.");
        }
        if ("USER".equals(entityType)) {
            if ("CREATE".equals(operation) || "UPDATE".equals(operation)) {
                requirePayload(record, issues, "email", "User email is required.");
                requirePayload(record, issues, "role", "User role is required.");
                requirePayload(record, issues, "department", "User department is required.");
            }
            if ("CREATE".equals(operation)) {
                requirePayload(record, issues, "password", "User password is required.");
            }
        }
        if ("DEPARTMENT".equals(entityType) && !"DELETE".equals(operation)) {
            requirePayload(record, issues, "name", "Department name is required.");
        }
        return issues;
    }

    public boolean hasBlockingIssues(List<SyncValidationIssueResponse> issues) {
        return issues != null && issues.stream().anyMatch(issue -> "ERROR".equalsIgnoreCase(issue.severity()));
    }

    private void requirePayload(SyncQueueRecordRequest record,
                                List<SyncValidationIssueResponse> issues,
                                String field,
                                String message) {
        if (record.payload() == null || normalize(record.payload().path(field).asText()).isBlank()) {
            issues.add(issue("ERROR", "MANDATORY_FIELD", record.entityType(), record.entityId(), message));
        }
    }

    private void requireAnyPayload(SyncQueueRecordRequest record,
                                   List<SyncValidationIssueResponse> issues,
                                   String message,
                                   String... fields) {
        if (record.payload() == null) {
            issues.add(issue("ERROR", "MANDATORY_FIELD", record.entityType(), record.entityId(), message));
            return;
        }
        for (String field : fields) {
            if (!normalize(record.payload().path(field).asText()).isBlank()) {
                return;
            }
        }
        issues.add(issue("ERROR", "MANDATORY_FIELD", record.entityType(), record.entityId(), message));
    }

    private void requirePayloadArray(SyncQueueRecordRequest record,
                                     List<SyncValidationIssueResponse> issues,
                                     String field,
                                     String message) {
        if (record.payload() == null || !record.payload().path(field).isArray() || record.payload().path(field).isEmpty()) {
            issues.add(issue("ERROR", "MANDATORY_FIELD", record.entityType(), record.entityId(), message));
        }
    }

    private SyncValidationIssueResponse issue(String severity, String category, String entityType, String entityId, String message) {
        return new SyncValidationIssueResponse(severity, category, entityType, entityId, message);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
