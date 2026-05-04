package com.mycompany.msr.amis;

import java.util.List;
import java.util.Map;

public interface AssignmentService {

    List<Assignment> getAssignments();

    List<Assignment> getAssignmentsPendingReturn();

    Map<String, Integer> getAvailableStockByCategory();

    int getAvailableStock(String equipmentType);

    int getDistributedCountForAssignment(int assignmentId);

    void createAssignment(String person, String department, String equipmentType, String reason, int quantity) throws Exception;

    void updateAssignment(int id, String person, String department, String equipmentType, String reason, int quantity) throws Exception;

    void updateAssignmentStatus(int id, String status) throws Exception;

    void deleteAssignment(int id) throws Exception;
}
