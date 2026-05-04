package com.mycompany.msr.amis.api.dto.returns;

import jakarta.validation.constraints.NotBlank;

public record ReturnItemRequest(
        @NotBlank String originalAssetCode,
        String enteredIdentifier,
        @NotBlank String returnedBy,
        @NotBlank String phone,
        @NotBlank String nid,
        @NotBlank String condition,
        String remarks,
        boolean replacement
) {
}
