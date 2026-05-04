package com.mycompany.msr.amis.api.dto.returns;

public record ReturnResponse(
        String assetCode,
        String returnedBy,
        String phone,
        String nid,
        String condition,
        String date
) {
}
