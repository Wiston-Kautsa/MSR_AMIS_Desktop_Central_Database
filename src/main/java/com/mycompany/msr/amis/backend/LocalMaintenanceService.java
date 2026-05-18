package com.mycompany.msr.amis;

import java.util.List;

public final class LocalMaintenanceService implements MaintenanceService {

    private final RemoteMirrorCoordinator remoteMirrorCoordinator = ServiceRegistry.getRemoteMirrorCoordinator();

    @Override
    public List<MaintenanceRecord> getMaintenanceRecords() {
        remoteMirrorCoordinator.synchronizeQuietlyIfOnline();
        return DatabaseHandler.getMaintenanceRecords();
    }

    @Override
    public void createMaintenanceRecord(String assetCode,
                                        String issue,
                                        String actionTaken,
                                        String performedBy,
                                        String maintenanceDate,
                                        String cost,
                                        boolean completed) throws Exception {
        if (remoteMirrorCoordinator.hasRemoteSession()) {
            remoteMirrorCoordinator.getRemoteMaintenanceService().createMaintenanceRecord(
                    assetCode,
                    issue,
                    actionTaken,
                    performedBy,
                    maintenanceDate,
                    cost,
                    completed
            );
            return;
        }
        DatabaseHandler.insertMaintenanceRecord(
                assetCode,
                issue,
                actionTaken,
                performedBy,
                maintenanceDate,
                cost,
                completed
        );
    }
}
