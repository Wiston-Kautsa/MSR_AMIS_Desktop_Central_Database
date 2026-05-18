package com.mycompany.msr.amis;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ApiReturnService implements ReturnService {

    private final ApiClient apiClient;

    public ApiReturnService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public List<Return> getReturns() {
        try {
            ReturnPayload[] payloads = apiClient.get("/api/returns", ReturnPayload[].class);
            return Arrays.stream(payloads == null ? new ReturnPayload[0] : payloads)
                    .map(ReturnPayload::toReturn)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IllegalStateException(resolveMessage(e), e);
        }
    }

    @Override
    public List<String> getOutstandingAssetCodesForAssignment(int assignmentId) {
        try {
            String[] payload = apiClient.get("/api/assignments/" + assignmentId + "/outstanding-assets", String[].class);
            return Arrays.asList(payload == null ? new String[0] : payload);
        } catch (Exception e) {
            throw new IllegalStateException(resolveMessage(e), e);
        }
    }

    @Override
    public ReturnSaveResult saveReturns(int assignmentId, String equipmentType, List<ReturnDraft> items, Map<String, String> outstandingRemarks) throws Exception {
        ReturnBatchRequest request = new ReturnBatchRequest();
        request.assignmentId = assignmentId;
        request.equipmentType = equipmentType;
        request.outstandingRemarks = outstandingRemarks;
        request.items = items.stream().map(ReturnItemRequest::from).collect(Collectors.toList());
        ReturnBatchResponse response = apiClient.post("/api/returns/complete", request, ReturnBatchResponse.class);
        ServiceRegistry.getRemoteMirrorCoordinator().refreshAfterRemoteMutation();
        if (outstandingRemarks != null && !outstandingRemarks.isEmpty()) {
            DatabaseHandler.updateOutstandingReturnRemarks(outstandingRemarks);
        }
        return new ReturnSaveResult(response.replacementAssetCodes == null ? List.of() : response.replacementAssetCodes);
    }

    private String resolveMessage(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank() ? "Failed to call the return API." : e.getMessage();
    }

    private static final class ReturnBatchRequest {
        public int assignmentId;
        public String equipmentType;
        public String outstandingRemark;
        public Map<String, String> outstandingRemarks;
        public List<ReturnItemRequest> items;
    }

    private static final class ReturnItemRequest {
        public String originalAssetCode;
        public String enteredIdentifier;
        public String returnedBy;
        public String phone;
        public String nid;
        public String condition;
        public String remarks;
        public boolean replacement;

        static ReturnItemRequest from(ReturnDraft draft) {
            ReturnItemRequest request = new ReturnItemRequest();
            request.originalAssetCode = draft.getOriginalAssetCode();
            request.enteredIdentifier = draft.getEnteredIdentifier();
            request.returnedBy = draft.getReturnedBy();
            request.phone = draft.getPhone();
            request.nid = draft.getNid();
            request.condition = draft.getCondition();
            request.remarks = draft.getRemarks();
            request.replacement = draft.isReplacement();
            return request;
        }
    }

    private static final class ReturnPayload {
        public String assetCode;
        public String returnedBy;
        public String phone;
        public String nid;
        public String condition;
        public String date;

        private Return toReturn() {
            return new Return(assetCode, returnedBy, phone, nid, condition, date);
        }
    }

    private static final class ReturnBatchResponse {
        public boolean success;
        public String message;
        public List<String> replacementAssetCodes;
    }
}
