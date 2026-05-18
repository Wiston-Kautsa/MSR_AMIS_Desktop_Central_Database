package com.mycompany.msr.amis;

import java.util.List;

public interface MaintenanceService {
    List<MaintenanceRecord> getMaintenanceRecords();

    void createMaintenanceRecord(String assetCode,
                                 String issue,
                                 String actionTaken,
                                 String performedBy,
                                 String maintenanceDate,
                                 String cost,
                                 boolean completed) throws Exception;
}
