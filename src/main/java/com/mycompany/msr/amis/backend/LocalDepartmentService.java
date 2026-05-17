package com.mycompany.msr.amis;

import java.util.List;
import java.util.Map;

public final class LocalDepartmentService implements DepartmentService {

    private final RemoteMirrorCoordinator remoteMirrorCoordinator = ServiceRegistry.getRemoteMirrorCoordinator();

    @Override
    public List<String> getDepartments() {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        return DatabaseHandler.getDepartments();
    }

    @Override
    public void createDepartment(String name) throws Exception {
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            remoteMirrorCoordinator.getRemoteDepartmentService().createDepartment(name);
            remoteMirrorCoordinator.synchronizeFromRemote(java.util.Map.of());
            return;
        }
        DatabaseHandler.insertDepartment(name);
        ServiceRegistry.getSyncCenterService().queueOperation(
                "DEPARTMENT",
                "CREATE",
                name,
                Map.of("name", name),
                null,
                "Offline department creation captured for later sync."
        );
    }

    @Override
    public void updateDepartment(String oldName, String newName) throws Exception {
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            remoteMirrorCoordinator.getRemoteDepartmentService().updateDepartment(oldName, newName);
            remoteMirrorCoordinator.synchronizeFromRemote(java.util.Map.of());
            return;
        }
        DatabaseHandler.updateDepartment(oldName, newName);
        ServiceRegistry.getSyncCenterService().queueOperation(
                "DEPARTMENT",
                "UPDATE",
                oldName,
                Map.of("oldName", oldName, "name", newName),
                Map.of("name", oldName),
                "Offline department update captured for later sync."
        );
    }

    @Override
    public void deleteDepartment(String name) throws Exception {
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            remoteMirrorCoordinator.getRemoteDepartmentService().deleteDepartment(name);
            remoteMirrorCoordinator.synchronizeFromRemote(java.util.Map.of());
            return;
        }
        DatabaseHandler.deleteDepartment(name);
        ServiceRegistry.getSyncCenterService().queueOperation(
                "DEPARTMENT",
                "DELETE",
                name,
                Map.of("name", name),
                Map.of("name", name),
                "Offline department deletion captured for later sync."
        );
    }
}
