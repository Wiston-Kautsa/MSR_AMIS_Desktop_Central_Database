package com.mycompany.msr.amis.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EquipmentFacadeServiceTest {

    @Test
    void generateAssetCodeNormalizesCategoryToThreeCharacters() {
        assertThat(EquipmentFacadeService.generateAssetCode("Tablet", 7))
                .isEqualTo("MSR-TAB-007");
    }

    @Test
    void generateAssetCodePadsShortCategory() {
        assertThat(EquipmentFacadeService.generateAssetCode("IT", 12))
                .isEqualTo("MSR-ITO-012");
    }
}
