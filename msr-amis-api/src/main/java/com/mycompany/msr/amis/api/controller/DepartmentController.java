package com.mycompany.msr.amis.api.controller;

import com.mycompany.msr.amis.api.dto.CommonMessageResponse;
import com.mycompany.msr.amis.api.dto.department.DepartmentRequest;
import com.mycompany.msr.amis.api.service.DepartmentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public List<String> listDepartments() {
        return departmentService.listDepartments();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public CommonMessageResponse createDepartment(Authentication authentication,
                                                  @Valid @RequestBody DepartmentRequest request) {
        String name = departmentService.createDepartment(authentication.getName(), request.name());
        return new CommonMessageResponse(true, "Department created: " + name);
    }

    @PutMapping("/{name}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public CommonMessageResponse updateDepartment(Authentication authentication,
                                                  @PathVariable String name,
                                                  @Valid @RequestBody DepartmentRequest request) {
        String updatedName = departmentService.updateDepartment(authentication.getName(), name, request.name());
        return new CommonMessageResponse(true, "Department updated: " + updatedName);
    }

    @DeleteMapping("/{name}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public CommonMessageResponse deleteDepartment(Authentication authentication, @PathVariable String name) {
        departmentService.deleteDepartment(authentication.getName(), name);
        return new CommonMessageResponse(true, "Department deleted successfully.");
    }
}
