package com.mycompany.msr.amis;

public final class AssetHistorySummary {

    private final String assetCode;
    private final String serialNumber;
    private final String equipmentName;
    private final String category;
    private final String entryDate;
    private final String currentStatus;

    public AssetHistorySummary(String assetCode,
                               String serialNumber,
                               String equipmentName,
                               String category,
                               String entryDate,
                               String currentStatus) {
        this.assetCode = safe(assetCode);
        this.serialNumber = safe(serialNumber);
        this.equipmentName = safe(equipmentName);
        this.category = safe(category);
        this.entryDate = safe(entryDate);
        this.currentStatus = safe(currentStatus);
    }

    public String getAssetCode() {
        return assetCode;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public String getCategory() {
        return category;
    }

    public String getEntryDate() {
        return entryDate;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
