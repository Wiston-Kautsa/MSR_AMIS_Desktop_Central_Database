package com.mycompany.msr.amis.api.controller;

import com.mycompany.msr.amis.api.domain.Department;
import com.mycompany.msr.amis.api.repository.DepartmentRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    public DepartmentController(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @GetMapping
    public List<String> listDepartments() {
        return departmentRepository.findAllByOrderByNameAsc().stream()
                .map(Department::getName)
                .toList();
    }
}
