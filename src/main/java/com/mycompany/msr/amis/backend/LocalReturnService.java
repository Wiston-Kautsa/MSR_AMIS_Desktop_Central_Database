package com.mycompany.msr.amis;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LocalReturnService implements ReturnService {

    private final RemoteMirrorCoordinator remoteMirrorCoordinator = ServiceRegistry.getRemoteMirrorCoordinator();

    @Override
    public List<Return> getReturns() {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        List<Return> returns = new ArrayList<>();
        try (Connection conn = DatabaseHandler.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT asset_code, returned_by, phone, nid, condition, return_date FROM returns ORDER BY return_date DESC, id DESC"
             )) {
            while (rs.next()) {
                returns.add(new Return(
                        rs.getString("asset_code"),
                        rs.getString("returned_by"),
                        rs.getString("phone"),
                        rs.getString("nid"),
                        rs.getString("condition"),
                        rs.getString("return_date")
                ));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load returns.", e);
        }
        return returns;
    }

    @Override
    public List<String> getOutstandingAssetCodesForAssignment(int assignmentId) {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        return DatabaseHandler.getOutstandingAssetCodesForAssignment(assignmentId);
    }

    @Override
    public ReturnSaveResult saveReturns(int assignmentId, String equipmentType, List<ReturnDraft> items, Map<String, String> outstandingRemarks) throws Exception {
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            ReturnSaveResult result = remoteMirrorCoordinator.getRemoteReturnService()
                    .saveReturns(assignmentId, equipmentType, items, outstandingRemarks);
            remoteMirrorCoordinator.synchronizeFromRemote(java.util.Map.of());
            if (outstandingRemarks != null && !outstandingRemarks.isEmpty()) {
                DatabaseHandler.updateOutstandingReturnRemarks(outstandingRemarks);
            }
            return result;
        }
        Assignment assignment = findAssignment(assignmentId);
        List<String> newAssetCodes = new ArrayList<>();
        List<String> remainingAssets = new ArrayList<>(DatabaseHandler.getOutstandingAssetCodesForAssignment(assignmentId));

        for (ReturnDraft item : items) {
            String originalAssetCode = item.getOriginalAssetCode();
            if (!removeOutstandingAsset(remainingAssets, originalAssetCode)) {
                throw new Exception("Asset is not outstanding for the selected assignment or was already returned: " + originalAssetCode);
            }

            String remarks = item.getRemarks();
            if (item.isReplacement()) {
                String newAssetCode = DatabaseHandler.insertReplacementEquipment(
                        equipmentType,
                        item.getEnteredIdentifier(),
                        "Replacement return for " + item.getOriginalAssetCode()
                );
                newAssetCodes.add(newAssetCode);
                remarks = appendRemark(
                        remarks,
                        "Replacement equipment recorded as " + newAssetCode +
                                " using IMEI/Serial " + item.getEnteredIdentifier()
                );
            }

            DatabaseHandler.returnEquipment(
                    item.getOriginalAssetCode(),
                    item.getReturnedBy(),
                    item.getPhone(),
                    item.getNid(),
                    item.getCondition(),
                    remarks
            );
        }

        if (!remainingAssets.isEmpty()) {
            DatabaseHandler.updateOutstandingReturnRemarks(outstandingRemarks);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assignment", assignmentPayload(assignment));
        payload.put("equipmentType", equipmentType);
        payload.put("outstandingRemarks", outstandingRemarks);
        payload.put("items", returnPayloads(items));
        ServiceRegistry.getSyncCenterService().queueOperation(
                "RETURN",
                "COMPLETE_BATCH",
                Integer.toString(assignmentId),
                payload,
                null,
                "Offline return batch captured for later sync."
        );

        return new ReturnSaveResult(newAssetCodes);
    }

    private String appendRemark(String existingRemarks, String extraRemark) {
        if (existingRemarks == null || existingRemarks.isBlank()) {
            return extraRemark;
        }
        return existingRemarks + " | " + extraRemark;
    }

    private boolean removeOutstandingAsset(List<String> remainingAssets, String assetCode) {
        for (int i = 0; i < remainingAssets.size(); i++) {
            String remainingAsset = remainingAssets.get(i);
            if (remainingAsset != null && assetCode != null && remainingAsset.trim().equalsIgnoreCase(assetCode.trim())) {
                remainingAssets.remove(i);
                return true;
            }
        }
        return false;
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

    private List<Map<String, Object>> returnPayloads(List<ReturnDraft> items) {
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (ReturnDraft item : items) {
            Equipment equipment = findEquipmentByAssetCode(item.getOriginalAssetCode());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("originalAssetCode", item.getOriginalAssetCode());
            payload.put("serialNumber", equipment == null ? "" : equipment.getSerialNumber());
            payload.put("enteredIdentifier", item.getEnteredIdentifier());
            payload.put("returnedBy", item.getReturnedBy());
            payload.put("phone", item.getPhone());
            payload.put("nid", item.getNid());
            payload.put("condition", item.getCondition());
            payload.put("remarks", item.getRemarks());
            payload.put("replacement", item.isReplacement());
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
