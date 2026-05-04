package com.mycompany.msr.amis.api.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of(
                "name", "MSR-AMIS API",
                "status", "running",
                "health", "/actuator/health"
        );
    }
}
