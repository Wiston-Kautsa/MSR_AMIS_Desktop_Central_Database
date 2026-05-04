package com.mycompany.msr.amis;

import java.util.List;

public interface DistributionService {

    List<String> getAvailableEquipment();

    List<String> getAvailableEquipmentByCategory(String category);

    List<Distribution> getCurrentDistributions();

    Distribution getCurrentDistributionForAsset(String assetCode);

    void distributeEquipmentBatch(int assignmentId, List<Distribution> distributions) throws Exception;
}
