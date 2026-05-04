package com.mycompany.msr.amis;

import java.util.List;

public interface ReturnService {

    List<Return> getReturns();

    List<String> getOutstandingAssetCodesForAssignment(int assignmentId);

    ReturnSaveResult saveReturns(int assignmentId, String equipmentType, List<ReturnDraft> items, String outstandingRemark) throws Exception;
}
