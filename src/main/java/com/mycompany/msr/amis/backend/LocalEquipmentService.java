package com.mycompany.msr.amis;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LocalEquipmentService implements EquipmentService {

    private final RemoteMirrorCoordinator remoteMirrorCoordinator = ServiceRegistry.getRemoteMirrorCoordinator();

    @Override
    public List<Equipment> getAllEquipment() {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        return DatabaseHandler.getAllEquipment();
    }

    @Override
    public List<String> getEquipmentCategories() {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        return DatabaseHandler.getEquipmentCategories();
    }

    @Override
    public void createEquipment(Equipment equipment) throws Exception {
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            remoteMirrorCoordinator.getRemoteEquipmentService().createEquipment(equipment);
            remoteMirrorCoordinator.synchronizeFromRemote(java.util.Map.of());
            return;
        }
        DatabaseHandler.insertEquipment(equipment);
        Equipment persisted = findEquipmentBySerialNumber(equipment.getSerialNumber());
        ServiceRegistry.getSyncCenterService().queueOperation(
                "EQUIPMENT",
                "CREATE",
                persisted == null ? equipment.getSerialNumber() : persisted.getAssetCode(),
                equipmentPayload(persisted == null ? equipment : persisted),
                null,
                "Offline equipment creation captured for later sync."
        );
    }

    @Override
    public void updateEquipment(String assetCode, String serialNumber, String name, String category, String condition) throws Exception {
        Equipment existing = findEquipmentByAssetCode(assetCode);
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            remoteMirrorCoordinator.getRemoteEquipmentService().updateEquipment(assetCode, serialNumber, name, category, condition);
            remoteMirrorCoordinator.synchronizeFromRemote(java.util.Map.of());
            return;
        }
        DatabaseHandler.updateEquipment(assetCode, serialNumber, name, category, condition);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assetCode", assetCode);
        payload.put("serialNumber", serialNumber);
        payload.put("name", name);
        payload.put("category", category);
        payload.put("condition", condition);
        ServiceRegistry.getSyncCenterService().queueOperation(
                "EQUIPMENT",
                "UPDATE",
                assetCode,
                payload,
                equipmentPayload(existing),
                "Offline equipment update captured for later sync."
        );
    }

    @Override
    public void updateEquipmentStatus(String assetCode, String status) throws Exception {
        Equipment existing = findEquipmentByAssetCode(assetCode);
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            remoteMirrorCoordinator.getRemoteEquipmentService().updateEquipmentStatus(assetCode, status);
            remoteMirrorCoordinator.synchronizeFromRemote(java.util.Map.of());
            return;
        }
        DatabaseHandler.updateEquipmentStatus(assetCode, status);
        ServiceRegistry.getSyncCenterService().queueOperation(
                "EQUIPMENT",
                "STATUS",
                assetCode,
                Map.of("assetCode", assetCode, "serialNumber", existing == null ? "" : existing.getSerialNumber(), "status", status),
                equipmentPayload(existing),
                "Offline equipment status change captured for later sync."
        );
    }

    @Override
    public void deleteEquipment(String assetCode) throws Exception {
        AccessControl.requireRole(AccessControl.ROLE_SUPER_ADMIN);
        Equipment existing = findEquipmentByAssetCode(assetCode);
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            remoteMirrorCoordinator.getRemoteEquipmentService().deleteEquipment(assetCode);
            remoteMirrorCoordinator.synchronizeFromRemote(java.util.Map.of());
            return;
        }
        DatabaseHandler.deleteEquipment(assetCode);
        ServiceRegistry.getSyncCenterService().queueOperation(
                "EQUIPMENT",
                "DELETE",
                assetCode,
                Map.of("assetCode", assetCode, "serialNumber", existing == null ? "" : existing.getSerialNumber()),
                equipmentPayload(existing),
                "Offline equipment deletion captured for later sync."
        );
    }

    private Equipment findEquipmentByAssetCode(String assetCode) {
        for (Equipment equipment : DatabaseHandler.getAllEquipment()) {
            if (assetCode != null && assetCode.equalsIgnoreCase(equipment.getAssetCode())) {
                return equipment;
            }
        }
        return null;
    }

    private Equipment findEquipmentBySerialNumber(String serialNumber) {
        for (Equipment equipment : DatabaseHandler.getAllEquipment()) {
            if (serialNumber != null && serialNumber.equalsIgnoreCase(equipment.getSerialNumber())) {
                return equipment;
            }
        }
        return null;
    }

    private Map<String, Object> equipmentPayload(Equipment equipment) {
        if (equipment == null) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assetCode", equipment.getAssetCode());
        payload.put("serialNumber", equipment.getSerialNumber());
        payload.put("name", equipment.getName());
        payload.put("category", equipment.getCategory());
        payload.put("condition", equipment.getCondition());
        payload.put("source", equipment.getSource());
        payload.put("entryDate", equipment.getEntryDate());
        payload.put("status", equipment.getStatus());
        return payload;
    }
}
