package com.mycompany.msr.amis.api.dto.distribution;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record DistributionBatchRequest(
        int assignmentId,
        @Valid @NotEmpty List<DistributionRequest> distributions
) {
}
