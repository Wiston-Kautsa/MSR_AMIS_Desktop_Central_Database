package com.mycompany.msr.amis;

import java.util.List;

public interface EquipmentService {

    List<Equipment> getAllEquipment();

    List<String> getEquipmentCategories();

    void createEquipment(Equipment equipment) throws Exception;

    void updateEquipment(String assetCode, String serialNumber, String name, String category, String condition) throws Exception;

    void updateEquipmentStatus(String assetCode, String status) throws Exception;

    void deleteEquipment(String assetCode) throws Exception;
}
