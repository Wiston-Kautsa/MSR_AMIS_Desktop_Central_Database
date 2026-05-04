package com.mycompany.msr.amis.api.controller;

import com.mycompany.msr.amis.api.dto.assignment.AssignmentRequest;
import com.mycompany.msr.amis.api.dto.assignment.AssignmentResponse;
import com.mycompany.msr.amis.api.dto.assignment.AssignmentStatusUpdateRequest;
import com.mycompany.msr.amis.api.service.OperationsService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final OperationsService operationsService;

    public AssignmentController(OperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @GetMapping
    public List<AssignmentResponse> getAssignments() {
        return operationsService.getAssignments();
    }

    @GetMapping("/pending-returns")
    public List<AssignmentResponse> getAssignmentsPendingReturn() {
        return operationsService.getAssignmentsPendingReturn();
    }

    @GetMapping("/available-stock")
    public Map<String, Integer> getAvailableStockByCategory() {
        return operationsService.getAvailableStockByCategory();
    }

    @GetMapping("/{id}/distributed-count")
    public Map<String, Integer> getDistributedCount(@PathVariable int id) {
        return Map.of("count", operationsService.getDistributedCountForAssignment(id));
    }

    @GetMapping("/{id}/outstanding-assets")
    public List<String> getOutstandingAssets(@PathVariable int id) {
        return operationsService.getOutstandingAssetCodes(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public AssignmentResponse createAssignment(@Valid @RequestBody AssignmentRequest request) {
        return operationsService.createAssignment(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public AssignmentResponse updateAssignment(@PathVariable int id, @Valid @RequestBody AssignmentRequest request) {
        return operationsService.updateAssignment(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public AssignmentResponse updateAssignmentStatus(@PathVariable int id,
                                                     @Valid @RequestBody AssignmentStatusUpdateRequest request) {
        return operationsService.updateAssignmentStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void deleteAssignment(@PathVariable int id) {
        operationsService.deleteAssignment(id);
    }
}
