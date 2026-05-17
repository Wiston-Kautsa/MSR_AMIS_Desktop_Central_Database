package com.mycompany.msr.amis.api.repository;

import com.mycompany.msr.amis.api.domain.EquipmentRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentRepository extends JpaRepository<EquipmentRecord, Long> {

    Optional<EquipmentRecord> findByAssetCodeIgnoreCase(String assetCode);

    @Query("select count(e) > 0 from EquipmentRecord e where lower(e.assetCode) = lower(:assetCode)")
    boolean existsByAssetCodeIgnoreCase(@Param("assetCode") String assetCode);

    boolean existsBySerialNumberIgnoreCase(String serialNumber);

    boolean existsBySerialNumberIgnoreCaseAndAssetCodeNotIgnoreCase(String serialNumber, String assetCode);

    Optional<EquipmentRecord> findBySerialNumberIgnoreCase(String serialNumber);

    @Query("select count(e) > 0 from EquipmentRecord e " +
            "where lower(e.assetCode) = lower(:identifier) and lower(e.assetCode) <> lower(:assetCode)")
    boolean existsByAssetCodeIgnoreCaseAndAssetCodeNotIgnoreCase(
            @Param("identifier") String identifier,
            @Param("assetCode") String assetCode
    );

    @Query("select distinct trim(e.category) from EquipmentRecord e order by trim(e.category)")
    List<String> findDistinctCategories();
}
