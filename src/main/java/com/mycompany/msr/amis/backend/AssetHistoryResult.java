package com.mycompany.msr.amis;

import java.util.List;

public final class AssetHistoryResult {

    private final AssetHistorySummary summary;
    private final List<AssetHistoryRecord> records;

    public AssetHistoryResult(AssetHistorySummary summary, List<AssetHistoryRecord> records) {
        this.summary = summary;
        this.records = records;
    }

    public AssetHistorySummary getSummary() {
        return summary;
    }

    public List<AssetHistoryRecord> getRecords() {
        return records;
    }
}
