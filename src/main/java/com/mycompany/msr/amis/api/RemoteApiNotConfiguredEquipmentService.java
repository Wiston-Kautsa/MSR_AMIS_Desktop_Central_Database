package com.mycompany.msr.amis;

import java.util.List;

public final class RemoteApiNotConfiguredEquipmentService implements EquipmentService {

    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException(
                "REMOTE_API mode is not implemented yet. Build the backend API and desktop HTTP client first."
        );
    }

    @Override
    public List<Equipment> getAllEquipment() {
        throw unsupported();
    }

    @Override
    public List<String> getEquipmentCategories() {
        throw unsupported();
    }

    @Override
    public void createEquipment(Equipment equipment) {
        throw unsupported();
    }

    @Override
    public void updateEquipment(String assetCode, String serialNumber, String name, String category, String condition) {
        throw unsupported();
    }

    @Override
    public void updateEquipmentStatus(String assetCode, String status) {
        throw unsupported();
    }

    @Override
    public void deleteEquipment(String assetCode) {
        throw unsupported();
    }
}
