package com.helixflow.modules.patient.controller;

import com.helixflow.common.dto.ApiResponse;
import com.helixflow.modules.patient.dto.ActiveEncounterDto;
import com.helixflow.modules.patient.entity.TriageAssessment;
import com.helixflow.modules.patient.entity.Visit;
import com.helixflow.modules.patient.repository.TriageAssessmentRepository;
import com.helixflow.modules.patient.repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/visits")
@RequiredArgsConstructor
public class VisitController {

    private final VisitRepository visitRepository;
    private final TriageAssessmentRepository triageAssessmentRepository;

    @GetMapping({"", "/active"})
    public ResponseEntity<ApiResponse<List<ActiveEncounterDto>>> getActiveVisits(
            @org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) String status) {
        log.info("Fetching active encounters via /api/v1/visits (status={})...", status);
        List<Visit> visits = visitRepository.findActiveEncounterVisits();

        List<ActiveEncounterDto> results = visits.stream().map(v -> {
            TriageAssessment triage = triageAssessmentRepository.findTopByVisitIdOrderByTriageTimeDesc(v.getId())
                    .orElse(null);
            return ActiveEncounterDto.fromVisit(v, triage);
        }).toList();

        return ResponseEntity.ok(ApiResponse.ok(results));
    }
}
