package com.mycompany.msr.amis;

import java.util.List;

public final class ReturnSaveResult {

    private final List<String> replacementAssetCodes;

    public ReturnSaveResult(List<String> replacementAssetCodes) {
        this.replacementAssetCodes = replacementAssetCodes;
    }

    public List<String> getReplacementAssetCodes() {
        return replacementAssetCodes;
    }
}
