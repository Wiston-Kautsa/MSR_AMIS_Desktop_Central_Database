package com.mycompany.msr.amis.api.controller;

import com.mycompany.msr.amis.api.dto.CommonMessageResponse;
import com.mycompany.msr.amis.api.dto.equipment.EquipmentRequest;
import com.mycompany.msr.amis.api.dto.equipment.EquipmentResponse;
import com.mycompany.msr.amis.api.dto.equipment.EquipmentStatusUpdateRequest;
import com.mycompany.msr.amis.api.service.EquipmentFacadeService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/equipment")
public class EquipmentController {

    private final EquipmentFacadeService equipmentFacadeService;

    public EquipmentController(EquipmentFacadeService equipmentFacadeService) {
        this.equipmentFacadeService = equipmentFacadeService;
    }

    @GetMapping
    public List<EquipmentResponse> getAll() {
        return equipmentFacadeService.getAll();
    }

    @GetMapping("/categories")
    public List<String> getCategories() {
        return equipmentFacadeService.getCategories();
    }

    @GetMapping("/{assetCode}")
    public EquipmentResponse getOne(@PathVariable String assetCode) {
        return equipmentFacadeService.getOne(assetCode);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public EquipmentResponse create(Authentication authentication, @Valid @RequestBody EquipmentRequest request) {
        return equipmentFacadeService.create(authentication.getName(), request);
    }

    @PutMapping("/{assetCode}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public EquipmentResponse update(Authentication authentication,
                                    @PathVariable String assetCode,
                                    @Valid @RequestBody EquipmentRequest request) {
        return equipmentFacadeService.update(authentication.getName(), assetCode, request);
    }

    @PatchMapping("/{assetCode}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public EquipmentResponse updateStatus(Authentication authentication,
                                          @PathVariable String assetCode,
                                          @Valid @RequestBody EquipmentStatusUpdateRequest request) {
        return equipmentFacadeService.updateStatus(authentication.getName(), assetCode, request.status());
    }

    @DeleteMapping("/{assetCode}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CommonMessageResponse delete(Authentication authentication, @PathVariable String assetCode) {
        equipmentFacadeService.delete(authentication.getName(), assetCode);
        return new CommonMessageResponse(true, "Equipment deleted successfully.");
    }
}
