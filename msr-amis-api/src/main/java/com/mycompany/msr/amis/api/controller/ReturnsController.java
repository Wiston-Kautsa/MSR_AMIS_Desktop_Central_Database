package com.mycompany.msr.amis.api.controller;

import com.mycompany.msr.amis.api.dto.returns.ReturnBatchRequest;
import com.mycompany.msr.amis.api.dto.returns.ReturnBatchResponse;
import com.mycompany.msr.amis.api.dto.returns.ReturnResponse;
import com.mycompany.msr.amis.api.service.OperationsService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/returns")
public class ReturnsController {

    private final OperationsService operationsService;

    public ReturnsController(OperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @GetMapping
    public List<ReturnResponse> getReturns() {
        return operationsService.getReturns();
    }

    @PostMapping("/complete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ReturnBatchResponse completeReturns(Authentication authentication, @Valid @RequestBody ReturnBatchRequest request) {
        return operationsService.completeReturns(authentication.getName(), request);
    }
}
