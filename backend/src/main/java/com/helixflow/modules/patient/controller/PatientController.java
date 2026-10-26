package com.helixflow.modules.patient.controller;

import com.helixflow.common.dto.ApiResponse;
import com.helixflow.modules.patient.dto.PatientRegistrationDto;
import com.helixflow.modules.patient.dto.PatientResponseDto;
import com.helixflow.modules.patient.dto.VisitCreateDto;
import com.helixflow.modules.patient.dto.VisitResponseDto;
import com.helixflow.modules.patient.service.PatientFlowService;
import com.helixflow.security.RequireRole;
import com.helixflow.security.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientFlowService patientFlowService;

    @PostMapping
    @RequireRole({Role.RECEPTIONIST, Role.NURSE, Role.ADMIN, Role.DOCTOR})
    public ResponseEntity<ApiResponse<PatientResponseDto>> registerPatient(@Valid @RequestBody PatientRegistrationDto dto) {
        PatientResponseDto response = patientFlowService.registerPatient(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PatientResponseDto>>> searchPatients(@RequestParam(value = "query", required = false) String query) {
        List<PatientResponseDto> patients = patientFlowService.searchPatients(query);
        return ResponseEntity.ok(ApiResponse.ok(patients));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PatientResponseDto>> getPatientById(@PathVariable("id") Long id) {
        PatientResponseDto patient = patientFlowService.getPatientById(id);
        return ResponseEntity.ok(ApiResponse.ok(patient));
    }

    @PostMapping("/{id}/visits")
    @RequireRole({Role.RECEPTIONIST, Role.NURSE, Role.ADMIN, Role.DOCTOR})
    public ResponseEntity<ApiResponse<VisitResponseDto>> createVisit(@PathVariable("id") Long patientId,
                                                                     @Valid @RequestBody VisitCreateDto dto) {
        dto.setPatientId(patientId);
        VisitResponseDto response = patientFlowService.createVisit(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }
}
