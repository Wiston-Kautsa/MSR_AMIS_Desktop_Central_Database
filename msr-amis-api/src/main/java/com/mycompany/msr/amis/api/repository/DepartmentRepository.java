package com.mycompany.msr.amis.api.repository;

import com.mycompany.msr.amis.api.domain.Department;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findAllByOrderByNameAsc();

    Optional<Department> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
