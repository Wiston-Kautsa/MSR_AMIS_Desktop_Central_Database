package com.mycompany.msr.amis.api.dto.asset;

public record AssetHistoryRecordResponse(
        String activityDate,
        String eventType,
        String actor,
        String affectedPerson,
        String details,
        String status
) {
}
