package com.mycompany.msr.amis.api.service;

import com.mycompany.msr.amis.api.domain.EquipmentRecord;
import com.mycompany.msr.amis.api.domain.EquipmentStatus;
import com.mycompany.msr.amis.api.dto.equipment.EquipmentRequest;
import com.mycompany.msr.amis.api.dto.equipment.EquipmentResponse;
import com.mycompany.msr.amis.api.exception.ApiException;
import com.mycompany.msr.amis.api.repository.EquipmentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentFacadeService {

    private final EquipmentRepository equipmentRepository;
    private final ActionAuditService actionAuditService;

    public EquipmentFacadeService(EquipmentRepository equipmentRepository,
                                  ActionAuditService actionAuditService) {
        this.equipmentRepository = equipmentRepository;
        this.actionAuditService = actionAuditService;
    }

    public List<EquipmentResponse> getAll() {
        return equipmentRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public EquipmentResponse getOne(String assetCode) {
        return toResponse(findByAssetCode(assetCode));
    }

    public List<String> getCategories() {
        return equipmentRepository.findDistinctCategories();
    }

    @Transactional
    public EquipmentResponse create(String actor, EquipmentRequest request) {
        ensureEquipmentIdentifierAvailable(request.serialNumber(), null);

        EquipmentRecord record = new EquipmentRecord();
        record.setAssetCode(generatePlaceholderAssetCode(request.category()));
        applyRequest(record, request);
        EquipmentRecord saved = equipmentRepository.saveAndFlush(record);
        saved.setAssetCode(generateAssetCode(saved.getCategory(), saved.getId()));
        EquipmentRecord persisted = equipmentRepository.save(saved);
        actionAuditService.log(
                actor,
                "ADD_EQUIPMENT",
                "EQUIPMENT",
                persisted.getAssetCode(),
                "Equipment added: " + persisted.getName() + ", category: " + persisted.getCategory() +
                        ", serial: " + persisted.getSerialNumber()
        );
        return toResponse(persisted);
    }

    @Transactional
    public EquipmentResponse update(String actor, String assetCode, EquipmentRequest request) {
        EquipmentRecord record = findByAssetCode(assetCode);
        String oldSnapshot = equipmentSnapshot(record);
        ensureEquipmentIdentifierAvailable(request.serialNumber(), assetCode);
        applyRequest(record, request);
        actionAuditService.log(
                actor,
                "EDIT_EQUIPMENT",
                "EQUIPMENT",
                record.getAssetCode(),
                "Equipment edited. Old: " + oldSnapshot + ". New: " + equipmentSnapshot(record)
        );
        return toResponse(record);
    }

    @Transactional
    public EquipmentResponse updateStatus(String actor, String assetCode, String rawStatus) {
        EquipmentRecord record = findByAssetCode(assetCode);
        EquipmentStatus nextStatus = parseStatus(rawStatus);
        if (record.getStatus() == EquipmentStatus.ASSIGNED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Assigned equipment cannot be retired or restored.");
        }
        record.setStatus(nextStatus);
        actionAuditService.log(
                actor,
                "EQUIPMENT_" + nextStatus.name(),
                "EQUIPMENT",
                record.getAssetCode(),
                "Equipment status changed to " + nextStatus.name()
        );
        return toResponse(record);
    }

    @Transactional
    public void delete(String actor, String assetCode) {
        EquipmentRecord record = findByAssetCode(assetCode);
        String oldSnapshot = equipmentSnapshot(record);
        equipmentRepository.delete(record);
        actionAuditService.log(actor, "DELETE_EQUIPMENT", "EQUIPMENT", assetCode, "Equipment deleted. Old: " + oldSnapshot);
    }

    private EquipmentRecord findByAssetCode(String assetCode) {
        return equipmentRepository.findByAssetCodeIgnoreCase(assetCode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Equipment not found."));
    }

    private void applyRequest(EquipmentRecord record, EquipmentRequest request) {
        record.setName(normalizeRequired(request.name(), "Equipment name is required."));
        record.setCategory(normalizeRequired(request.category(), "Category is required."));
        record.setSerialNumber(normalizeRequired(request.serialNumber(), "Serial number is required."));
        record.setSource(normalizeOptional(request.source()));
        record.setItemCondition(normalizeOptional(request.condition()));
        record.setEntryDate(request.entryDate() == null ? LocalDate.now() : request.entryDate());
        if (record.getStatus() == null) {
            record.setStatus(EquipmentStatus.AVAILABLE);
        }
    }

    private EquipmentResponse toResponse(EquipmentRecord record) {
        return new EquipmentResponse(
                record.getId(),
                record.getAssetCode(),
                record.getName(),
                record.getCategory(),
                record.getSerialNumber(),
                record.getItemCondition(),
                record.getSource(),
                record.getEntryDate(),
                record.getStatus().name()
        );
    }

    private String equipmentSnapshot(EquipmentRecord record) {
        if (record == null) {
            return "not found";
        }
        return "asset=" + normalizeOptional(record.getAssetCode()) +
                ", name=" + normalizeOptional(record.getName()) +
                ", category=" + normalizeOptional(record.getCategory()) +
                ", serial=" + normalizeOptional(record.getSerialNumber()) +
                ", condition=" + normalizeOptional(record.getItemCondition()) +
                ", source=" + normalizeOptional(record.getSource()) +
                ", status=" + (record.getStatus() == null ? "" : record.getStatus().name());
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private void ensureEquipmentIdentifierAvailable(String identifier, String currentAssetCode) {
        String normalizedIdentifier = normalizeRequired(identifier, "Serial number is required.");
        if (currentAssetCode == null || currentAssetCode.isBlank()) {
            if (equipmentRepository.existsBySerialNumberIgnoreCase(normalizedIdentifier)
                    || equipmentRepository.existsByAssetCodeIgnoreCase(normalizedIdentifier)) {
                throw new ApiException(HttpStatus.CONFLICT, "Asset code or serial number already exists.");
            }
            return;
        }

        if (equipmentRepository.existsBySerialNumberIgnoreCaseAndAssetCodeNotIgnoreCase(normalizedIdentifier, currentAssetCode)
                || equipmentRepository.existsByAssetCodeIgnoreCaseAndAssetCodeNotIgnoreCase(normalizedIdentifier, currentAssetCode)) {
            throw new ApiException(HttpStatus.CONFLICT, "Asset code or serial number already exists.");
        }
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private EquipmentStatus parseStatus(String rawStatus) {
        String normalized = normalizeRequired(rawStatus, "Status is required.").toUpperCase(Locale.ROOT);
        if ("ACTIVE".equals(normalized)) {
            return EquipmentStatus.AVAILABLE;
        }
        try {
            return EquipmentStatus.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid equipment status.");
        }
    }

    static String generateAssetCode(String category, long id) {
        return "MSR-" + normalizeCategoryCode(category) + "-" + String.format("%03d", id);
    }

    private String generatePlaceholderAssetCode(String category) {
        return "TMP-" + normalizeCategoryCode(category) + "-" + System.nanoTime();
    }

    private static String normalizeCategoryCode(String category) {
        String normalizedCategory = (category == null ? "" : category.trim())
                .toUpperCase(Locale.ROOT)
                .replace(" ", "");
        if (normalizedCategory.length() < 3) {
            normalizedCategory = (normalizedCategory + "OTH").substring(0, 3);
        } else {
            normalizedCategory = normalizedCategory.substring(0, 3);
        }
        return normalizedCategory;
    }
}
