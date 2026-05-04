package com.mycompany.msr.amis.api.controller;

import com.mycompany.msr.amis.api.dto.dashboard.DashboardSummaryResponse;
import com.mycompany.msr.amis.api.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final AnalyticsService analyticsService;

    public DashboardController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse getSummary() {
        return analyticsService.getDashboardSummary();
    }
}
