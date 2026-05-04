package com.mycompany.msr.amis.api.dto.distribution;

public record DistributionResponse(
        int id,
        String assetCode,
        String assignedTo,
        String phone,
        String nid,
        String date
) {
}
