package com.mycompany.msr.amis;

import java.util.List;
import java.util.Map;

public interface ReturnService {

    List<Return> getReturns();

    List<String> getOutstandingAssetCodesForAssignment(int assignmentId);

    default ReturnSaveResult saveReturns(int assignmentId, String equipmentType, List<ReturnDraft> items, String outstandingRemark) throws Exception {
        return saveReturns(assignmentId, equipmentType, items, Map.of());
    }

    ReturnSaveResult saveReturns(int assignmentId, String equipmentType, List<ReturnDraft> items, Map<String, String> outstandingRemarks) throws Exception;
}
