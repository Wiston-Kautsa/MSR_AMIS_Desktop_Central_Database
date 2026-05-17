package com.mycompany.msr.amis.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mycompany.msr.amis.api.dto.sync.EquipmentSyncRequest;
import com.mycompany.msr.amis.api.dto.sync.SyncQueueRecordRequest;
import com.mycompany.msr.amis.api.exception.ApiException;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class EquipmentSyncHandler {

    private final EquipmentFacadeService equipmentFacadeService;
    private final SyncIdempotencyRepository idempotencyRepository;

    public EquipmentSyncHandler(EquipmentFacadeService equipmentFacadeService,
                                SyncIdempotencyRepository idempotencyRepository) {
        this.equipmentFacadeService = equipmentFacadeService;
        this.idempotencyRepository = idempotencyRepository;
    }

    public String apply(String actor, String machineId, SyncQueueRecordRequest record) {
        String entityType = normalize(record.entityType()).toUpperCase(Locale.ROOT);
        String operation = normalize(record.operation()).toUpperCase(Locale.ROOT);
        if (!"EQUIPMENT".equals(entityType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported sync entity: " + record.entityType());
        }
        if (idempotencyRepository.alreadyProcessed(record.idempotencyKey())) {
            return "Already processed";
        }

        String assetCode;
        String message;
        Object processedPayload;
        switch (operation) {
            case "CREATE":
            case "UPDATE":
            case "UPSERT":
                EquipmentSyncRequest request = toEquipmentRequest(record);
                equipmentFacadeService.syncEquipment(actor, request);
                assetCode = request.assetCode();
                processedPayload = request;
                message = "Equipment " + operation.toLowerCase(Locale.ROOT) + " applied";
                break;
            case "DELETE":
                assetCode = firstNonBlank(text(record.payload(), "assetCode"), record.entityId());
                equipmentFacadeService.syncDeleteEquipment(actor, assetCode);
                processedPayload = record.payload();
                message = "Equipment delete applied";
                break;
            case "STATUS":
                assetCode = firstNonBlank(text(record.payload(), "assetCode"), record.entityId());
                equipmentFacadeService.updateStatus(actor, assetCode, text(record.payload(), "status"));
                processedPayload = record.payload();
                message = "Equipment status applied";
                break;
            default:
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported equipment sync operation: " + operation);
        }

        idempotencyRepository.markProcessed(
                actor,
                machineId,
                record.idempotencyKey(),
                "EQUIPMENT",
                assetCode,
                operation,
                processedPayload
        );
        return message;
    }

    private EquipmentSyncRequest toEquipmentRequest(SyncQueueRecordRequest record) {
        JsonNode payload = record.payload();
        if (payload == null || payload.isNull() || payload.isMissingNode()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Equipment payload is required.");
        }
        return new EquipmentSyncRequest(
                firstNonBlank(record.idempotencyKey(), text(payload, "idempotencyKey")),
                text(payload, "assetCode"),
                text(payload, "name"),
                text(payload, "category"),
                text(payload, "serialNumber"),
                firstNonBlank(text(payload, "condition"), text(payload, "itemCondition")),
                text(payload, "source"),
                date(payload, "entryDate"),
                text(payload, "status"),
                text(payload, "purchaseCost"),
                text(payload, "location"),
                date(payload, "warrantyExpiry"),
                text(payload, "supplier")
        );
    }

    private String text(JsonNode payload, String field) {
        JsonNode value = payload.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private LocalDate date(JsonNode payload, String field) {
        String value = text(payload, field);
        if (value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid " + field + " date.");
        }
    }

    private String firstNonBlank(String first, String second) {
        return normalize(first).isBlank() ? normalize(second) : normalize(first);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
