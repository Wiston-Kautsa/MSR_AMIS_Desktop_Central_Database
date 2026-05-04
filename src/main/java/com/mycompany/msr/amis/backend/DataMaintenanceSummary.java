package com.mycompany.msr.amis;

public final class DataMaintenanceSummary {

    private final int equipmentCount;
    private final int assignmentCount;
    private final int distributionCount;
    private final int returnCount;
    private final int auditLogCount;

    public DataMaintenanceSummary(int equipmentCount,
                                  int assignmentCount,
                                  int distributionCount,
                                  int returnCount,
                                  int auditLogCount) {
        this.equipmentCount = equipmentCount;
        this.assignmentCount = assignmentCount;
        this.distributionCount = distributionCount;
        this.returnCount = returnCount;
        this.auditLogCount = auditLogCount;
    }

    public int getEquipmentCount() {
        return equipmentCount;
    }

    public int getAssignmentCount() {
        return assignmentCount;
    }

    public int getDistributionCount() {
        return distributionCount;
    }

    public int getReturnCount() {
        return returnCount;
    }

    public int getAuditLogCount() {
        return auditLogCount;
    }
}
