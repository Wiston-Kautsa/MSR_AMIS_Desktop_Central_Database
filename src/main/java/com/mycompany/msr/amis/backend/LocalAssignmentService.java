package com.mycompany.msr.amis;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LocalAssignmentService implements AssignmentService {

    private final RemoteMirrorCoordinator remoteMirrorCoordinator = ServiceRegistry.getRemoteMirrorCoordinator();

    @Override
    public List<Assignment> getAssignments() {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        List<Assignment> assignments = DatabaseHandler.getAssignments();
        hydrateAssignments(assignments);
        return assignments;
    }

    @Override
    public List<Assignment> getAssignmentsPendingReturn() {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        List<Assignment> assignments = DatabaseHandler.getAssignmentsPendingReturn();
        hydrateAssignments(assignments);
        return assignments;
    }

    @Override
    public Map<String, Integer> getAvailableStockByCategory() {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        return DatabaseHandler.getAvailableStockByCategory();
    }

    @Override
    public int getAvailableStock(String equipmentType) {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        return DatabaseHandler.getAvailableStock(equipmentType);
    }

    @Override
    public int getDistributedCountForAssignment(int assignmentId) {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        return DatabaseHandler.getDistributedCountForAssignment(assignmentId);
    }

    @Override
    public void createAssignment(String person, String department, String equipmentType, String reason, int quantity) throws Exception {
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            remoteMirrorCoordinator.getRemoteAssignmentService().createAssignment(person, department, equipmentType, reason, quantity);
            remoteMirrorCoordinator.synchronizeFromRemote(java.util.Map.of());
            return;
        }
        DatabaseHandler.insertAssignment(person, department, equipmentType, reason, quantity);
        ServiceRegistry.getSyncCenterService().queueOperation(
                "ASSIGNMENT",
                "CREATE",
                person + "|" + equipmentType,
                assignmentPayload(new Assignment(0, person, department, equipmentType, reason, quantity, "")),
                null,
                "Offline assignment creation captured for later sync."
        );
    }

    @Override
    public void updateAssignment(int id, String person, String department, String equipmentType, String reason, int quantity) throws Exception {
        Assignment existing = findAssignment(id);
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            remoteMirrorCoordinator.getRemoteAssignmentService().updateAssignment(id, person, department, equipmentType, reason, quantity);
            remoteMirrorCoordinator.synchronizeFromRemote(java.util.Map.of());
            return;
        }
        DatabaseHandler.updateAssignment(id, person, department, equipmentType, reason, quantity);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("person", person);
        payload.put("department", department);
        payload.put("equipmentType", equipmentType);
        payload.put("reason", reason);
        payload.put("quantity", quantity);
        ServiceRegistry.getSyncCenterService().queueOperation(
                "ASSIGNMENT",
                "UPDATE",
                Integer.toString(id),
                payload,
                assignmentPayload(existing),
                "Offline assignment update captured for later sync."
        );
    }

    @Override
    public void updateAssignmentStatus(int id, String status) throws Exception {
        Assignment existing = findAssignment(id);
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            remoteMirrorCoordinator.getRemoteAssignmentService().updateAssignmentStatus(id, status);
            remoteMirrorCoordinator.synchronizeFromRemote(java.util.Map.of());
            return;
        }
        DatabaseHandler.updateAssignmentStatus(id, status);
        ServiceRegistry.getSyncCenterService().queueOperation(
                "ASSIGNMENT",
                "STATUS",
                Integer.toString(id),
                Map.of(
                        "id", id,
                        "person", existing == null ? "" : existing.getPerson(),
                        "department", existing == null ? "" : existing.getDepartment(),
                        "equipmentType", existing == null ? "" : existing.getEquipmentType(),
                        "reason", existing == null ? "" : existing.getReason(),
                        "quantity", existing == null ? 0 : existing.getQuantity(),
                        "status", status
                ),
                assignmentPayload(existing),
                "Offline assignment status change captured for later sync."
        );
    }

    @Override
    public void deleteAssignment(int id) throws Exception {
        AccessControl.requireRole(AccessControl.ROLE_SUPER_ADMIN);
        Assignment existing = findAssignment(id);
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            remoteMirrorCoordinator.getRemoteAssignmentService().deleteAssignment(id);
            remoteMirrorCoordinator.synchronizeFromRemote(java.util.Map.of());
            return;
        }
        DatabaseHandler.deleteAssignment(id);
        ServiceRegistry.getSyncCenterService().queueOperation(
                "ASSIGNMENT",
                "DELETE",
                Integer.toString(id),
                assignmentPayload(existing),
                assignmentPayload(existing),
                "Offline assignment deletion captured for later sync."
        );
    }

    private void hydrateAssignments(List<Assignment> assignments) {
        for (Assignment assignment : assignments) {
            int distributed = DatabaseHandler.getDistributedCountForAssignment(assignment.getId());
            assignment.setDistributedCount(distributed);
            assignment.setStatus(deriveStatus(assignment.getId(), distributed, assignment.getQuantity()));
        }
    }

    private String deriveStatus(int assignmentId, int distributed, int quantity) {
        String lifecycleStatus = DatabaseHandler.getAssignmentLifecycleStatus(assignmentId);
        if (AccessControl.STATUS_FROZEN.equalsIgnoreCase(lifecycleStatus)
                || AccessControl.STATUS_RETIRED.equalsIgnoreCase(lifecycleStatus)) {
            return lifecycleStatus;
        }
        if (distributed == 0) {
            return "PENDING";
        }
        if (distributed < quantity) {
            return "PARTIAL";
        }
        return "COMPLETE";
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
        payload.put("status", assignment.getStatus());
        payload.put("date", assignment.getDate());
        return payload;
    }
}
