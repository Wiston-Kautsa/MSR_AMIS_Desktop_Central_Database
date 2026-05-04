package com.mycompany.msr.amis.api.dto.dashboard;

public record DashboardSummaryResponse(
        int totalAssets,
        int availableAssets,
        int borrowedThisMonth,
        int stillInUseFromBorrowedThisMonth,
        int returnedThisMonth,
        int assetsInUse,
        int outstandingWithRemarks
) {
}
