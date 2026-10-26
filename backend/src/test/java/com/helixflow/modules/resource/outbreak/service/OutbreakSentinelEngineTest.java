package com.helixflow.modules.resource.outbreak.service;

import com.helixflow.modules.patient.entity.Visit;
import com.helixflow.modules.patient.repository.VisitRepository;
import com.helixflow.modules.resource.outbreak.dto.ClusterScanResultDto;
import com.helixflow.modules.resource.outbreak.dto.OutbreakAlertDto;
import com.helixflow.modules.resource.outbreak.entity.AlertLevel;
import com.helixflow.modules.resource.outbreak.entity.OutbreakAlert;
import com.helixflow.modules.resource.outbreak.repository.OutbreakAlertRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutbreakSentinelEngineTest {

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private OutbreakAlertRepository alertRepository;

    @InjectMocks
    private OutbreakSentinelEngine sentinelEngine;

    private List<Visit> visits;

    @BeforeEach
    void setUp() {
        visits = new ArrayList<>();
        Instant now = Instant.now();

        // Generate 12 respiratory/febrile complaints in the past 12 hours (well above baseline mean=3.0, std=1.2)
        for (int i = 1; i <= 12; i++) {
            visits.add(Visit.builder()
                    .id((long) i)
                    .visitNumber("ENC-" + i)
                    .arrivalTime(now.minus(i, ChronoUnit.HOURS))
                    .chiefComplaint("Patient presents with high fever, persistent dry cough, and shortness of breath")
                    .build());
        }

        // Generate 3 gastrointestinal complaints
        for (int i = 13; i <= 15; i++) {
            visits.add(Visit.builder()
                    .id((long) i)
                    .visitNumber("ENC-" + i)
                    .arrivalTime(now.minus(4, ChronoUnit.HOURS))
                    .chiefComplaint("Acute abdominal pain and frequent vomiting")
                    .build());
        }
    }

    @Test
    @DisplayName("Syndromic scan should detect respiratory cluster and trigger OUTBREAK_TRIGGER alert")
    void testRunSurveillanceScan_DetectsRespiratoryCluster() {
        when(visitRepository.findAll()).thenReturn(visits);
        when(alertRepository.save(any(OutbreakAlert.class))).thenAnswer(invocation -> {
            OutbreakAlert a = invocation.getArgument(0);
            a.setId(100L);
            return a;
        });

        ClusterScanResultDto scanResult = sentinelEngine.runSurveillanceScan();

        assertNotNull(scanResult);
        assertEquals(15, scanResult.getTotalVisitsScanned());
        assertEquals(12, scanResult.getSyndromeDistribution().get("RESPIRATORY_FEBRILE"));
        assertEquals(3, scanResult.getSyndromeDistribution().get("GASTROINTESTINAL"));

        // With 12 respiratory visits, z = (12 - 3.0) / 1.2 = 7.5 >= 2.58 -> OUTBREAK_TRIGGER
        assertFalse(scanResult.getGeneratedAlerts().isEmpty());
        OutbreakAlertDto alert = scanResult.getGeneratedAlerts().stream()
                .filter(a -> "RESPIRATORY_FEBRILE".equals(a.getSyndromeCluster()))
                .findFirst()
                .orElse(null);

        assertNotNull(alert);
        assertEquals(AlertLevel.OUTBREAK_TRIGGER, alert.getAlertLevel());
        assertEquals(12, alert.getDetectedCount());
        assertTrue(alert.getZScore() >= 2.58);
        verify(alertRepository, atLeastOnce()).save(any(OutbreakAlert.class));
    }

    @Test
    @DisplayName("Acknowledge alert updates acknowledged flag to true")
    void testAcknowledgeAlert() {
        OutbreakAlert alert = OutbreakAlert.builder()
                .id(42L)
                .syndromeCluster("RESPIRATORY_FEBRILE")
                .alertLevel(AlertLevel.WARNING)
                .acknowledged(false)
                .build();

        when(alertRepository.findById(42L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(OutbreakAlert.class))).thenAnswer(i -> i.getArgument(0));

        OutbreakAlertDto result = sentinelEngine.acknowledgeAlert(42L);

        assertNotNull(result);
        assertTrue(result.isAcknowledged());
        verify(alertRepository).save(alert);
    }
}
