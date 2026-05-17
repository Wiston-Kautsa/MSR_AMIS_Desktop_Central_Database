package com.mycompany.msr.amis.api.service;

import com.mycompany.msr.amis.api.domain.Department;
import com.mycompany.msr.amis.api.exception.ApiException;
import com.mycompany.msr.amis.api.repository.DepartmentRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {

    private static final String DEFAULT_DEPARTMENT = "MSR";

    private final DepartmentRepository departmentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ActionAuditService actionAuditService;

    public DepartmentService(DepartmentRepository departmentRepository,
                             JdbcTemplate jdbcTemplate,
                             ActionAuditService actionAuditService) {
        this.departmentRepository = departmentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.actionAuditService = actionAuditService;
    }

    public List<String> listDepartments() {
        ensureDefaultDepartment();
        return departmentRepository.findAllByOrderByNameAsc().stream()
                .map(Department::getName)
                .toList();
    }

    @Transactional
    public String createDepartment(String actor, String rawName) {
        String name = normalizeRequired(rawName);
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "Department already exists.");
        }
        Department department = new Department();
        department.setName(name);
        departmentRepository.save(department);
        actionAuditService.log(actor, "CREATE_DEPARTMENT", "DEPARTMENTS", name, "Department created: " + name);
        return name;
    }

    @Transactional
    public String updateDepartment(String actor, String oldName, String rawNewName) {
        String currentName = normalizeRequired(oldName);
        String newName = normalizeRequired(rawNewName);
        if (DEFAULT_DEPARTMENT.equalsIgnoreCase(currentName)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The default MSR department cannot be renamed.");
        }
        Department department = departmentRepository.findByNameIgnoreCase(currentName)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Department not found."));
        departmentRepository.findByNameIgnoreCase(newName)
                .filter(existing -> !existing.getId().equals(department.getId()))
                .ifPresent(existing -> {
                    throw new ApiException(HttpStatus.CONFLICT, "Department already exists.");
                });

        jdbcTemplate.update("UPDATE users SET department = ? WHERE TRIM(department) = TRIM(?)", newName, currentName);
        jdbcTemplate.update("UPDATE assignments SET department = ? WHERE TRIM(department) = TRIM(?)", newName, currentName);
        department.setName(newName);
        departmentRepository.save(department);
        actionAuditService.log(actor, "EDIT_DEPARTMENT", "DEPARTMENTS", currentName, "Department renamed from " + currentName + " to " + newName);
        return newName;
    }

    @Transactional
    public void deleteDepartment(String actor, String rawName) {
        String name = normalizeRequired(rawName);
        if (DEFAULT_DEPARTMENT.equalsIgnoreCase(name)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The default MSR department cannot be deleted.");
        }
        Department department = departmentRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Department not found."));
        if (isDepartmentInUse(name)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Department is still in use by users or assignments.");
        }
        departmentRepository.delete(department);
        actionAuditService.log(actor, "DELETE_DEPARTMENT", "DEPARTMENTS", name, "Department deleted: " + name);
    }

    private void ensureDefaultDepartment() {
        if (!departmentRepository.existsByNameIgnoreCase(DEFAULT_DEPARTMENT)) {
            Department department = new Department();
            department.setName(DEFAULT_DEPARTMENT);
            departmentRepository.save(department);
        }
    }

    private boolean isDepartmentInUse(String name) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT " +
                        "(SELECT COUNT(*) FROM users WHERE TRIM(department)=TRIM(?)) + " +
                        "(SELECT COUNT(*) FROM assignments WHERE TRIM(department)=TRIM(?))",
                Integer.class,
                name,
                name
        );
        return count != null && count > 0;
    }

    private String normalizeRequired(String value) {
        if (value == null || value.trim().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Department name is required.");
        }
        return value.trim();
    }
}
