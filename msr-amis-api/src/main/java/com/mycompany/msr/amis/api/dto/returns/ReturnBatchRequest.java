package com.mycompany.msr.amis.api.dto.returns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReturnBatchRequest(
        int assignmentId,
        @NotBlank String equipmentType,
        String outstandingRemark,
        @Valid @NotEmpty List<ReturnItemRequest> items
) {
}
