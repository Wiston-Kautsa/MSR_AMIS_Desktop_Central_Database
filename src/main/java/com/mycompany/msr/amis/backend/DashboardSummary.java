package com.mycompany.msr.amis;

public final class DashboardSummary {

    private final int totalAssets;
    private final int availableAssets;
    private final int borrowedThisMonth;
    private final int stillInUseFromBorrowedThisMonth;
    private final int returnedThisMonth;
    private final int assetsInUse;
    private final int outstandingWithRemarks;

    public DashboardSummary(int totalAssets,
                            int availableAssets,
                            int borrowedThisMonth,
                            int stillInUseFromBorrowedThisMonth,
                            int returnedThisMonth,
                            int assetsInUse,
                            int outstandingWithRemarks) {
        this.totalAssets = totalAssets;
        this.availableAssets = availableAssets;
        this.borrowedThisMonth = borrowedThisMonth;
        this.stillInUseFromBorrowedThisMonth = stillInUseFromBorrowedThisMonth;
        this.returnedThisMonth = returnedThisMonth;
        this.assetsInUse = assetsInUse;
        this.outstandingWithRemarks = outstandingWithRemarks;
    }

    public int getTotalAssets() {
        return totalAssets;
    }

    public int getAvailableAssets() {
        return availableAssets;
    }

    public int getBorrowedThisMonth() {
        return borrowedThisMonth;
    }

    public int getStillInUseFromBorrowedThisMonth() {
        return stillInUseFromBorrowedThisMonth;
    }

    public int getReturnedThisMonth() {
        return returnedThisMonth;
    }

    public int getAssetsInUse() {
        return assetsInUse;
    }

    public int getOutstandingWithRemarks() {
        return outstandingWithRemarks;
    }
}
