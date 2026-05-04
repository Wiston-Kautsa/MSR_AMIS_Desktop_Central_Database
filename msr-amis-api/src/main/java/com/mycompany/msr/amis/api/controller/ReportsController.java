package com.mycompany.msr.amis.api.controller;

import com.mycompany.msr.amis.api.dto.assignment.AssignmentResponse;
import com.mycompany.msr.amis.api.dto.report.DistributionReportItemResponse;
import com.mycompany.msr.amis.api.dto.report.InventoryReportItemResponse;
import com.mycompany.msr.amis.api.dto.report.ReturnReportItemResponse;
import com.mycompany.msr.amis.api.service.AnalyticsService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportsController {

    private final AnalyticsService analyticsService;

    public ReportsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/inventory")
    public List<InventoryReportItemResponse> getInventoryReport() {
        return analyticsService.getInventoryReport();
    }

    @GetMapping("/assignments")
    public List<AssignmentResponse> getAssignmentReport() {
        return analyticsService.getAssignmentReport();
    }

    @GetMapping("/distributions")
    public List<DistributionReportItemResponse> getDistributionReport() {
        return analyticsService.getDistributionReport();
    }

    @GetMapping("/returns")
    public List<ReturnReportItemResponse> getReturnReport() {
        return analyticsService.getReturnReport();
    }

    @GetMapping("/outstanding")
    public List<DistributionReportItemResponse> getOutstandingReport() {
        return analyticsService.getOutstandingReport();
    }
}
