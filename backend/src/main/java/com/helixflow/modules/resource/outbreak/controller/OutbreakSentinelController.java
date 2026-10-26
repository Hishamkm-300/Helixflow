package com.helixflow.modules.resource.outbreak.controller;

import com.helixflow.common.dto.ApiResponse;
import com.helixflow.modules.resource.outbreak.dto.ClusterScanResultDto;
import com.helixflow.modules.resource.outbreak.dto.OutbreakAlertDto;
import com.helixflow.modules.resource.outbreak.service.OutbreakSentinelEngine;
import com.helixflow.security.RequireRole;
import com.helixflow.security.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sentinel")
@RequiredArgsConstructor
public class OutbreakSentinelController {

    private final OutbreakSentinelEngine sentinelEngine;

    @GetMapping("/alerts")
    public ResponseEntity<ApiResponse<List<OutbreakAlertDto>>> getActiveAlerts() {
        return ResponseEntity.ok(ApiResponse.ok(sentinelEngine.getActiveAlerts()));
    }

    @PostMapping({"/trigger-scan", "/scan"})
    @RequireRole({Role.ADMIN, Role.DOCTOR})
    public ResponseEntity<ApiResponse<ClusterScanResultDto>> triggerManualScan() {
        ClusterScanResultDto result = sentinelEngine.runSurveillanceScan();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @RequestMapping(value = {"/alerts/{id}/acknowledge", "/alerts/{id}/ack"}, method = {RequestMethod.PUT, RequestMethod.POST})
    @RequireRole({Role.ADMIN, Role.DOCTOR, Role.NURSE})
    public ResponseEntity<ApiResponse<OutbreakAlertDto>> acknowledgeAlert(@PathVariable("id") Long id) {
        OutbreakAlertDto alert = sentinelEngine.acknowledgeAlert(id);
        return ResponseEntity.ok(ApiResponse.ok(alert));
    }
}
