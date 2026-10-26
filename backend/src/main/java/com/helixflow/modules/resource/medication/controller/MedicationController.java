package com.helixflow.modules.resource.medication.controller;

import com.helixflow.common.dto.ApiResponse;
import com.helixflow.modules.resource.medication.dto.*;
import com.helixflow.modules.resource.medication.service.MedicationSafetyService;
import com.helixflow.security.RequireRole;
import com.helixflow.security.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/medications")
@RequiredArgsConstructor
public class MedicationController {

    private final MedicationSafetyService medicationSafetyService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MedicationDto>>> searchMedications(@RequestParam(value = "query", required = false) String query) {
        return ResponseEntity.ok(ApiResponse.ok(medicationSafetyService.searchMedications(query)));
    }

    @PostMapping({"/check-interactions", "/safety-check"})
    public ResponseEntity<ApiResponse<SafetyCheckResultDto>> evaluateSafety(@Valid @RequestBody MedicationSafetyCheckRequest request) {
        SafetyCheckResultDto result = medicationSafetyService.evaluateSafety(request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping({"/orders", "/prescribe"})
    @RequireRole({Role.DOCTOR, Role.ADMIN})
    public ResponseEntity<ApiResponse<MedicationOrderDto>> prescribeMedication(@Valid @RequestBody MedicationPrescribeRequest request) {
        MedicationOrderDto order = medicationSafetyService.prescribeMedication(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(order));
    }

    @PutMapping("/orders/{orderId}/discontinue")
    @RequireRole({Role.DOCTOR, Role.ADMIN})
    public ResponseEntity<ApiResponse<MedicationOrderDto>> discontinueOrder(
            @PathVariable("orderId") Long orderId,
            @RequestBody(required = false) MedicationOrderDiscontinueRequest request) {
        String reason = (request != null && request.getReason() != null) ? request.getReason() : "Discontinued by clinician";
        MedicationOrderDto order = medicationSafetyService.discontinueMedicationOrder(orderId, reason);
        return ResponseEntity.ok(ApiResponse.ok(order));
    }

    @GetMapping({"/orders/visit/{visitId}", "/orders/{visitId}"})
    public ResponseEntity<ApiResponse<List<MedicationOrderDto>>> getOrdersForVisit(
            @PathVariable("visitId") Long visitId,
            @RequestParam(value = "activeOnly", required = false, defaultValue = "false") boolean activeOnly) {
        List<MedicationOrderDto> orders = activeOnly
                ? medicationSafetyService.getActiveOrdersForVisit(visitId)
                : medicationSafetyService.getOrdersForVisit(visitId);
        return ResponseEntity.ok(ApiResponse.ok(orders));
    }
}
