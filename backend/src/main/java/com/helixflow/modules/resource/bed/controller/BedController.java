package com.helixflow.modules.resource.bed.controller;

import com.helixflow.common.dto.ApiResponse;
import com.helixflow.modules.resource.bed.dto.BedAdmissionDto;
import com.helixflow.modules.resource.bed.dto.BedAllocationDto;
import com.helixflow.modules.resource.bed.dto.BedCapacitySummaryDto;
import com.helixflow.modules.resource.bed.dto.BedDto;
import com.helixflow.modules.resource.bed.entity.BedStatus;
import com.helixflow.modules.resource.bed.service.BedManagementService;
import com.helixflow.security.RequireRole;
import com.helixflow.security.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/beds")
@RequiredArgsConstructor
public class BedController {

    private final BedManagementService bedManagementService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BedDto>>> getBeds(
            @RequestParam(value = "wardId", required = false) Long wardId,
            @RequestParam(value = "status", required = false) BedStatus status) {
        List<BedDto> beds = bedManagementService.getBeds(wardId, status);
        return ResponseEntity.ok(ApiResponse.ok(beds));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<BedCapacitySummaryDto>> getCapacitySummary() {
        BedCapacitySummaryDto summary = bedManagementService.getCapacitySummary();
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @PostMapping("/admit")
    @RequireRole({Role.NURSE, Role.DOCTOR, Role.ADMIN})
    public ResponseEntity<ApiResponse<BedAdmissionDto>> admitPatient(@Valid @RequestBody BedAllocationDto dto) {
        BedAdmissionDto admission = bedManagementService.admitPatient(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(admission));
    }

    @PostMapping("/{id}/discharge")
    @RequireRole({Role.NURSE, Role.DOCTOR, Role.ADMIN})
    public ResponseEntity<ApiResponse<BedAdmissionDto>> dischargePatient(@PathVariable("id") Long bedId) {
        BedAdmissionDto admission = bedManagementService.dischargePatient(bedId);
        return ResponseEntity.ok(ApiResponse.ok(admission));
    }

    @PostMapping("/{id}/cleaned")
    @RequireRole({Role.NURSE, Role.ADMIN})
    public ResponseEntity<ApiResponse<BedDto>> markBedCleaned(@PathVariable("id") Long bedId) {
        BedDto bed = bedManagementService.markBedCleaned(bedId);
        return ResponseEntity.ok(ApiResponse.ok(bed));
    }
}
