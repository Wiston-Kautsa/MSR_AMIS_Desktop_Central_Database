package com.mycompany.msr.amis;

public interface DataMaintenanceService {

    DataMaintenanceSummary getSummary() throws Exception;

    String resetComponent(String component) throws Exception;
}
