package com.helixflow.modules.patient.controller;

import com.helixflow.common.dto.ApiResponse;
import com.helixflow.modules.patient.dto.TriageAssessmentDto;
import com.helixflow.modules.patient.dto.TriageQueueItemDto;
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
@RequestMapping("/api/v1/triage")
@RequiredArgsConstructor
public class TriageController {

    private final PatientFlowService patientFlowService;

    @PostMapping
    @RequireRole({Role.NURSE, Role.DOCTOR, Role.ADMIN})
    public ResponseEntity<ApiResponse<TriageQueueItemDto>> recordTriage(@Valid @RequestBody TriageAssessmentDto dto) {
        TriageQueueItemDto result = patientFlowService.recordTriage(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }

    @GetMapping("/queue")
    public ResponseEntity<ApiResponse<List<TriageQueueItemDto>>> getActiveTriageQueue() {
        List<TriageQueueItemDto> queue = patientFlowService.getActiveTriageQueue();
        return ResponseEntity.ok(ApiResponse.ok(queue));
    }
}
