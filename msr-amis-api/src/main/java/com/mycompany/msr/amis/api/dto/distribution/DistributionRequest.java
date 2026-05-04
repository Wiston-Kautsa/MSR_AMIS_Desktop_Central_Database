package com.mycompany.msr.amis.api.dto.distribution;

import jakarta.validation.constraints.NotBlank;

public record DistributionRequest(
        @NotBlank String assetCode,
        @NotBlank String assignedTo,
        @NotBlank String phone,
        @NotBlank String nid
) {
}
