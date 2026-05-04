package com.mycompany.msr.amis.api.repository;

import com.mycompany.msr.amis.api.domain.EquipmentRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EquipmentRepository extends JpaRepository<EquipmentRecord, Long> {

    Optional<EquipmentRecord> findByAssetCodeIgnoreCase(String assetCode);

    boolean existsBySerialNumberIgnoreCase(String serialNumber);

    boolean existsBySerialNumberIgnoreCaseAndAssetCodeNotIgnoreCase(String serialNumber, String assetCode);

    @Query("select distinct trim(e.category) from EquipmentRecord e order by trim(e.category)")
    List<String> findDistinctCategories();
}
