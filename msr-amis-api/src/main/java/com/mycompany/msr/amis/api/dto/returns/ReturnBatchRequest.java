package com.mycompany.msr.amis.api.dto.returns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public record ReturnBatchRequest(
        int assignmentId,
        @NotBlank String equipmentType,
        String outstandingRemark,
        Map<String, String> outstandingRemarks,
        @Valid @NotEmpty List<ReturnItemRequest> items
) {
}
