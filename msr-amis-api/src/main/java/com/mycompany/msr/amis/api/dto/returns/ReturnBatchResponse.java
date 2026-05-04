package com.mycompany.msr.amis.api.dto.returns;

import java.util.List;

public record ReturnBatchResponse(
        boolean success,
        String message,
        List<String> replacementAssetCodes
) {
}
