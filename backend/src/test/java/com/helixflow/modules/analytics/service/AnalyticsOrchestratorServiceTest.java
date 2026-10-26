package com.helixflow.modules.analytics.service;

import com.helixflow.modules.analytics.dto.*;
import com.helixflow.modules.analytics.ml.FallbackRuleBasedPredictor;
import com.helixflow.modules.analytics.simulation.HospitalCapacitySimulator;
import com.helixflow.modules.patient.entity.Patient;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsOrchestratorServiceTest {

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private TriageAssessmentRepository triageRepository;

    @Mock
    private BedRepository bedRepository;

    @Mock
    private WardRepository wardRepository;

    @Mock
    private BedAdmissionRepository bedAdmissionRepository;

    @Mock
    private FallbackRuleBasedPredictor rulePredictor;

    @Mock
    private HospitalCapacitySimulator capacitySimulator;

    @InjectMocks
    private AnalyticsOrchestratorService analyticsService;

    @Test
    @DisplayName("getEdCongestionForecast queries current queue and bed counts, delegating to rule predictor")
    void testGetEdCongestionForecast() {
        when(visitRepository.countByStatus(VisitStatus.TRIAGE_WAIT)).thenReturn(8L);
        when(triageRepository.countWaitingByEsiLevel(1)).thenReturn(1L);
        when(triageRepository.countWaitingByEsiLevel(2)).thenReturn(2L);
        when(bedRepository.count()).thenReturn(20L);
        when(bedRepository.countByStatus(BedStatus.OCCUPIED)).thenReturn(15L);

        CongestionForecastDto mockForecast = CongestionForecastDto.builder()
                .targetTime(java.time.LocalDateTime.now().plusHours(2))
                .estimatedWaitMinutes(95.0)
                .predictedQueueDepth(10)
                .severityLevel("HIGH")
                .modelSource("RULE_BASED_FALLBACK")
                .build();

        when(rulePredictor.predictCongestion(eq(8), eq(3), eq(0.75))).thenReturn(mockForecast);

        CongestionForecastDto result = analyticsService.getEdCongestionForecast();

        assertNotNull(result);
        assertEquals("HIGH", result.getSeverityLevel());
        assertEquals(95.0, result.getEstimatedWaitMinutes());
        assertEquals(10, result.getPredictedQueueDepth());
        verify(rulePredictor).predictCongestion(8, 3, 0.75);
    }

    @Test
    @DisplayName("getBedDemandForecast returns projected admissions and shortage risks for all wards")
    void testGetBedDemandForecast() {
        Ward icu = Ward.builder().id(1L).code("ICU-MAIN").name("Intensive Care Unit").totalCapacity(4).build();
        Ward ed = Ward.builder().id(2L).code("ED-ACUTE").name("Emergency Acute").totalCapacity(8).build();

        when(wardRepository.findAll()).thenReturn(List.of(icu, ed));
        when(bedRepository.countByWardIdAndStatus(1L, BedStatus.OCCUPIED)).thenReturn(4L);
        when(bedRepository.countByWardIdAndStatus(1L, BedStatus.AVAILABLE)).thenReturn(0L);
        when(bedRepository.countByWardIdAndStatus(2L, BedStatus.OCCUPIED)).thenReturn(4L);
        when(bedRepository.countByWardIdAndStatus(2L, BedStatus.AVAILABLE)).thenReturn(4L);

        List<BedDemandForecastDto> forecasts = analyticsService.getBedDemandForecast();

        assertNotNull(forecasts);
        assertEquals(2, forecasts.size());

        BedDemandForecastDto icuForecast = forecasts.stream()
                .filter(f -> "ICU-MAIN".equals(f.getWardCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(icuForecast);
        assertEquals("CRITICAL", icuForecast.getShortageRiskLevel());
        assertTrue(icuForecast.getNetBedBalance() <= 0);
    }

    @Test
    @DisplayName("detectFlowAnomalies identifies unallocated ESI-1 patients immediately, extended wait times, and surges")
    void testDetectFlowAnomalies() {
        Instant now = Instant.now();
        List<Visit> activeVisits = new ArrayList<>();

        for (int i = 1; i <= 9; i++) {
            Patient p = Patient.builder().firstName("Patient").lastName("Num" + i).build();
            Visit v = Visit.builder().id((long) i).visitNumber("ENC-" + i).patient(p).arrivalTime(now.minus(60, ChronoUnit.MINUTES)).build();
            activeVisits.add(v);

            TriageAssessment t = TriageAssessment.builder().visit(v).esiLevel(i == 1 ? 1 : 2).build();
            when(triageRepository.findTopByVisitIdOrderByTriageTimeDesc((long) i)).thenReturn(java.util.Optional.of(t));
            when(bedAdmissionRepository.findTopByVisitIdAndDischargedAtIsNull((long) i)).thenReturn(java.util.Optional.empty());
        }

        when(visitRepository.findActiveEdVisits()).thenReturn(activeVisits);

        List<FlowAnomalyDto> anomalies = analyticsService.detectFlowAnomalies();

        assertNotNull(anomalies);
        assertFalse(anomalies.isEmpty());
        // Should detect CRITICAL UNALLOCATED ESI-1, UNALLOCATED ESI-2 / EXTENDED_ED_DWELL, and ACUTE_SURGE
        assertTrue(anomalies.stream().anyMatch(a -> "CRITICAL UNALLOCATED ESI-1".equals(a.getAnomalyType())));
        assertTrue(anomalies.stream().anyMatch(a -> "CRITICAL".equals(a.getSeverity())));
        assertTrue(anomalies.stream().anyMatch(a -> "ACUTE_SURGE".equals(a.getAnomalyType())));
    }

    @Test
    @DisplayName("runWhatIfSimulation queries bed capacities and delegates to simulator")
    void testRunWhatIfSimulation() {
        WhatIfSimulationRequest request = WhatIfSimulationRequest.builder()
                .simulationHours(12)
                .deltaArrivalRatePercent(20)
                .deltaStaffingPercent(0)
                .closedBedsCount(2)
                .build();

        when(bedRepository.count()).thenReturn(22L);
        when(bedRepository.countByStatus(BedStatus.OCCUPIED)).thenReturn(14L);

        WhatIfSimulationResult mockResult = WhatIfSimulationResult.builder()
                .peakQueueDepth(12)
                .systemStressLevel("STRAINED")
                .mitigationRecommendations(List.of("Monitor queue"))
                .build();

        when(capacitySimulator.runSimulation(request, 22, 14)).thenReturn(mockResult);

        WhatIfSimulationResult result = analyticsService.runWhatIfSimulation(request);

        assertNotNull(result);
        assertEquals("STRAINED", result.getSystemStressLevel());
        verify(capacitySimulator).runSimulation(request, 22, 14);
    }
}
