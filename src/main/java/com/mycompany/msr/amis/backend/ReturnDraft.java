package com.mycompany.msr.amis;

public final class ReturnDraft {

    private final String originalAssetCode;
    private final String enteredIdentifier;
    private final String returnedBy;
    private final String phone;
    private final String nid;
    private final String condition;
    private final String remarks;
    private final boolean replacement;

    public ReturnDraft(String originalAssetCode,
                       String enteredIdentifier,
                       String returnedBy,
                       String phone,
                       String nid,
                       String condition,
                       String remarks,
                       boolean replacement) {
        this.originalAssetCode = originalAssetCode;
        this.enteredIdentifier = enteredIdentifier;
        this.returnedBy = returnedBy;
        this.phone = phone;
        this.nid = nid;
        this.condition = condition;
        this.remarks = remarks;
        this.replacement = replacement;
    }

    public String getOriginalAssetCode() {
        return originalAssetCode;
    }

    public String getEnteredIdentifier() {
        return enteredIdentifier;
    }

    public String getReturnedBy() {
        return returnedBy;
    }

    public String getPhone() {
        return phone;
    }

    public String getNid() {
        return nid;
    }

    public String getCondition() {
        return condition;
    }

    public String getRemarks() {
        return remarks;
    }

    public boolean isReplacement() {
        return replacement;
    }
}
