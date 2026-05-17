package com.mycompany.msr.amis;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LocalDistributionService implements DistributionService {

    private final RemoteMirrorCoordinator remoteMirrorCoordinator = ServiceRegistry.getRemoteMirrorCoordinator();

    @Override
    public List<String> getAvailableEquipment() {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        return DatabaseHandler.getAvailableEquipment();
    }

    @Override
    public List<String> getAvailableEquipmentByCategory(String category) {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        return DatabaseHandler.getAvailableEquipmentByCategory(category);
    }

    @Override
    public List<Distribution> getCurrentDistributions() {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        return DatabaseHandler.getCurrentDistributions();
    }

    @Override
    public Distribution getCurrentDistributionForAsset(String assetCode) {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        return DatabaseHandler.getCurrentDistributionForAsset(assetCode);
    }

    @Override
    public void distributeEquipmentBatch(int assignmentId, List<Distribution> distributions) throws Exception {
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            remoteMirrorCoordinator.getRemoteDistributionService().distributeEquipmentBatch(assignmentId, distributions);
            remoteMirrorCoordinator.synchronizeFromRemote(java.util.Map.of());
            return;
        }
        Assignment assignment = findAssignment(assignmentId);
        DatabaseHandler.distributeEquipmentBatch(assignmentId, distributions);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assignment", assignmentPayload(assignment));
        payload.put("items", distributionPayloads(distributions));
        ServiceRegistry.getSyncCenterService().queueOperation(
                "DISTRIBUTION",
                "BATCH_CREATE",
                Integer.toString(assignmentId),
                payload,
                null,
                "Offline distribution batch captured for later sync."
        );
    }

    private Assignment findAssignment(int id) {
        for (Assignment assignment : DatabaseHandler.getAssignments()) {
            if (assignment.getId() == id) {
                return assignment;
            }
        }
        return null;
    }

    private Map<String, Object> assignmentPayload(Assignment assignment) {
        if (assignment == null) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", assignment.getId());
        payload.put("person", assignment.getPerson());
        payload.put("department", assignment.getDepartment());
        payload.put("equipmentType", assignment.getEquipmentType());
        payload.put("reason", assignment.getReason());
        payload.put("quantity", assignment.getQuantity());
        return payload;
    }

    private List<Map<String, Object>> distributionPayloads(List<Distribution> distributions) {
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (Distribution distribution : distributions) {
            Equipment equipment = findEquipmentByAssetCode(distribution.getAssetCode());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("assetCode", distribution.getAssetCode());
            payload.put("serialNumber", equipment == null ? "" : equipment.getSerialNumber());
            payload.put("assignedTo", distribution.getAssignedTo());
            payload.put("phone", distribution.getPhone());
            payload.put("nid", distribution.getNid());
            payload.put("date", distribution.getDate());
            payloads.add(payload);
        }
        return payloads;
    }

    private Equipment findEquipmentByAssetCode(String assetCode) {
        for (Equipment equipment : DatabaseHandler.getAllEquipment()) {
            if (assetCode != null && assetCode.equalsIgnoreCase(equipment.getAssetCode())) {
                return equipment;
            }
        }
        return null;
    }
}
