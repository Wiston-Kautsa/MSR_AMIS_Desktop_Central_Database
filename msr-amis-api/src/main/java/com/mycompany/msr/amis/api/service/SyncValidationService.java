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
            requirePayload(record, issues, "assetCode", "Asset code is required.");
            if (!"DELETE".equals(operation) && !"STATUS".equals(operation)) {
                requirePayload(record, issues, "serialNumber", "Serial number is required.");
            }
        }
        if ("RETURN".equals(entityType)) {
            requirePayload(record, issues, "assetCode", "Returned asset code is required.");
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

    private SyncValidationIssueResponse issue(String severity, String category, String entityType, String entityId, String message) {
        return new SyncValidationIssueResponse(severity, category, entityType, entityId, message);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
