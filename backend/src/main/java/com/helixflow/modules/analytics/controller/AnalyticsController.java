package com.helixflow.modules.analytics.controller;

import com.helixflow.common.dto.ApiResponse;
import com.helixflow.modules.analytics.dto.*;
import com.helixflow.modules.analytics.service.AnalyticsOrchestratorService;
import com.helixflow.security.RequireRole;
import com.helixflow.security.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsOrchestratorService analyticsService;

    @GetMapping({"/ed-congestion/forecast", "/congestion-forecast"})
    public ResponseEntity<ApiResponse<CongestionForecastDto>> getEdCongestionForecast() {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getEdCongestionForecast()));
    }

    @GetMapping({"/bed-demand/forecast", "/bed-demand"})
    public ResponseEntity<ApiResponse<List<BedDemandForecastDto>>> getBedDemandForecast() {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getBedDemandForecast()));
    }

    @GetMapping("/anomalies")
    public ResponseEntity<ApiResponse<List<FlowAnomalyDto>>> getFlowAnomalies() {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.detectFlowAnomalies()));
    }

    @PostMapping({"/simulation/what-if", "/simulate"})
    @RequireRole({Role.ADMIN, Role.DOCTOR})
    public ResponseEntity<ApiResponse<WhatIfSimulationResult>> runWhatIfSimulation(
            @Valid @RequestBody WhatIfSimulationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.runWhatIfSimulation(request)));
    }
}
