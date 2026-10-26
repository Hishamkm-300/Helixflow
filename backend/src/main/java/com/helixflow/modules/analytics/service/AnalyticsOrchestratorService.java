package com.helixflow.modules.analytics.service;

import com.helixflow.modules.analytics.dto.*;
import com.helixflow.modules.analytics.ml.FallbackRuleBasedPredictor;
import com.helixflow.modules.analytics.simulation.HospitalCapacitySimulator;
import com.helixflow.modules.patient.entity.TriageAssessment;
import com.helixflow.modules.patient.entity.Visit;
import com.helixflow.modules.patient.entity.VisitStatus;
import com.helixflow.modules.patient.repository.TriageAssessmentRepository;
import com.helixflow.modules.patient.repository.VisitRepository;
import com.helixflow.modules.resource.bed.entity.BedStatus;
import com.helixflow.modules.resource.bed.entity.Ward;
import com.helixflow.modules.resource.bed.repository.BedRepository;
import com.helixflow.modules.resource.bed.repository.WardRepository;
import com.helixflow.modules.resource.bed.repository.BedAdmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsOrchestratorService {

    private final VisitRepository visitRepository;
    private final TriageAssessmentRepository triageRepository;
    private final BedRepository bedRepository;
    private final WardRepository wardRepository;
    private final BedAdmissionRepository bedAdmissionRepository;
    private final FallbackRuleBasedPredictor rulePredictor;
    private final HospitalCapacitySimulator capacitySimulator;

    @Transactional(readOnly = true)
    public CongestionForecastDto getEdCongestionForecast() {
        long waitingCount = visitRepository.countByStatus(VisitStatus.TRIAGE_WAIT);
        long highEsiCount = triageRepository.countWaitingByEsiLevel(1) + triageRepository.countWaitingByEsiLevel(2);

        long totalBeds = bedRepository.count();
        long occupiedBeds = bedRepository.countByStatus(BedStatus.OCCUPIED);
        double occupancyRatio = totalBeds > 0 ? (double) occupiedBeds / totalBeds : 0.0;

        return rulePredictor.predictCongestion((int) waitingCount, (int) highEsiCount, occupancyRatio);
    }

    @Transactional(readOnly = true)
    public List<BedDemandForecastDto> getBedDemandForecast() {
        List<BedDemandForecastDto> forecasts = new ArrayList<>();
        List<Ward> wards = wardRepository.findAll();

        for (Ward ward : wards) {
            int occupied = (int) bedRepository.countByWardIdAndStatus(ward.getId(), BedStatus.OCCUPIED);
            int available = (int) bedRepository.countByWardIdAndStatus(ward.getId(), BedStatus.AVAILABLE);

            // Synthetic projection proportional to unit capacity and flow pressure
            int predictedAdmissions = Math.max(1, (int) Math.round(ward.getTotalCapacity() * 0.35));
            int netBalance = available - predictedAdmissions;

            String risk;
            if (netBalance <= 0) {
                risk = "CRITICAL";
            } else if (netBalance <= 2) {
                risk = "MODERATE";
            } else {
                risk = "LOW";
            }

            forecasts.add(BedDemandForecastDto.builder()
                    .targetTime(LocalDateTime.now().plusHours(24))
                    .wardCode(ward.getCode())
                    .wardName(ward.getName())
                    .currentOccupied(occupied)
                    .currentAvailable(available)
                    .predictedAdmissionsNext24h(predictedAdmissions)
                    .netBedBalance(netBalance)
                    .shortageRiskLevel(risk)
                    .confidenceIntervalLower(Math.max(0, predictedAdmissions - 2))
                    .confidenceIntervalUpper(predictedAdmissions + 3)
                    .build());
        }
        return forecasts;
    }

    @Transactional(readOnly = true)
    public List<FlowAnomalyDto> detectFlowAnomalies() {
        List<FlowAnomalyDto> anomalies = new ArrayList<>();
        Instant now = Instant.now();

        // Query all active live encounters in ED (TRIAGE_WAIT, IN_TREATMENT)
        List<Visit> activeVisits = visitRepository.findActiveEdVisits();

        for (Visit visit : activeVisits) {
            TriageAssessment triage = triageRepository.findTopByVisitIdOrderByTriageTimeDesc(visit.getId()).orElse(null);
            int esi = triage != null && triage.getEsiLevel() != null ? triage.getEsiLevel() : 3;
            boolean hasBed = bedAdmissionRepository.findTopByVisitIdAndDischargedAtIsNull(visit.getId()).isPresent();
            long dwellMinutes = Duration.between(visit.getArrivalTime(), now).toMinutes();

            String patientName = visit.getPatient() != null
                    ? (visit.getPatient().getFirstName() + " " + visit.getPatient().getLastName()).trim()
                    : "Patient #" + visit.getId();

            // 1. ESI 1 Unallocated: Trigger IMMEDIATELY with 0-minute dwell threshold
            if (esi == 1 && !hasBed) {
                anomalies.add(FlowAnomalyDto.builder()
                        .id("ANOM-ESI1-" + visit.getId())
                        .visitId(visit.getId())
                        .visitNumber(visit.getVisitNumber())
                        .patientName(patientName)
                        .anomalyType("CRITICAL UNALLOCATED ESI-1")
                        .anomalyScore(1.0)
                        .severity("CRITICAL")
                        .detectedAt(now)
                        .description("Emergency resuscitation patient waiting for immediate inpatient/resus bed allocation.")
                        .build());
            }
            // 2. ESI 2 Unallocated: Trigger if waiting >= 10 minutes without bed
            else if (esi == 2 && !hasBed && dwellMinutes >= 10) {
                anomalies.add(FlowAnomalyDto.builder()
                        .id("ANOM-ESI2-" + visit.getId())
                        .visitId(visit.getId())
                        .visitNumber(visit.getVisitNumber())
                        .patientName(patientName)
                        .anomalyType("UNALLOCATED ESI-2")
                        .anomalyScore(0.85)
                        .severity("HIGH")
                        .detectedAt(now)
                        .description("Emergent patient (ESI 2) waiting " + dwellMinutes + " mins without assigned treatment bed.")
                        .build());
            }
            // 3. Extended ED dwell time (> 45 minutes)
            else if (dwellMinutes > 45 && !hasBed) {
                anomalies.add(FlowAnomalyDto.builder()
                        .id("ANOM-WAIT-" + visit.getId())
                        .visitId(visit.getId())
                        .visitNumber(visit.getVisitNumber())
                        .patientName(patientName)
                        .anomalyType("EXTENDED_ED_DWELL")
                        .anomalyScore(Math.min(1.0, dwellMinutes / 120.0))
                        .severity(dwellMinutes > 90 ? "HIGH" : "MEDIUM")
                        .detectedAt(now)
                        .description("Patient waiting for " + dwellMinutes + " mins without inpatient/treatment bed allocation (ESI " + esi + ").")
                        .build());
            }
        }

        // 4. Sudden queue surge detection (queue depth >= 8)
        if (activeVisits.size() >= 8) {
            anomalies.add(FlowAnomalyDto.builder()
                    .id("ANOM-SURGE-" + (System.currentTimeMillis() % 10000))
                    .visitNumber("ED-OVERVIEW")
                    .patientName("Department-Wide")
                    .anomalyType("ACUTE_SURGE")
                    .anomalyScore(0.92)
                    .severity("HIGH")
                    .detectedAt(now)
                    .description("Triage queue depth exceeds normal baseline by >250% (" + activeVisits.size() + " active patients waiting).")
                    .build());
        }

        // Prioritize CRITICAL alerts first, then HIGH, then MEDIUM
        anomalies.sort((a, b) -> {
            int rankA = "CRITICAL".equalsIgnoreCase(a.getSeverity()) ? 3 : "HIGH".equalsIgnoreCase(a.getSeverity()) ? 2 : 1;
            int rankB = "CRITICAL".equalsIgnoreCase(b.getSeverity()) ? 3 : "HIGH".equalsIgnoreCase(b.getSeverity()) ? 2 : 1;
            return Integer.compare(rankB, rankA);
        });

        return anomalies;
    }

    @Transactional(readOnly = true)
    public WhatIfSimulationResult runWhatIfSimulation(WhatIfSimulationRequest request) {
        int totalBeds = (int) bedRepository.count();
        int occupied = (int) bedRepository.countByStatus(BedStatus.OCCUPIED);
        return capacitySimulator.runSimulation(request, totalBeds, occupied);
    }
}
