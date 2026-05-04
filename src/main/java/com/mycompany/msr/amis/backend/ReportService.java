package com.mycompany.msr.amis;

import java.util.List;

public interface ReportService {

    List<Equipment> getInventoryReport();

    List<Assignment> getAssignmentReport();

    List<Distribution> getDistributionReport();

    List<ReturnRecord> getReturnReport();

    List<Distribution> getOutstandingReport();
}
