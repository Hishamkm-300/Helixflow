package com.helixflow.modules.resource.outbreak.service;

import com.helixflow.common.exception.ResourceNotFoundException;
import com.helixflow.modules.patient.entity.Visit;
import com.helixflow.modules.patient.repository.VisitRepository;
import com.helixflow.modules.resource.outbreak.dto.ClusterScanResultDto;
import com.helixflow.modules.resource.outbreak.dto.OutbreakAlertDto;
import com.helixflow.modules.resource.outbreak.entity.AlertLevel;
import com.helixflow.modules.resource.outbreak.entity.OutbreakAlert;
import com.helixflow.modules.resource.outbreak.repository.OutbreakAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutbreakSentinelEngine {

    private final VisitRepository visitRepository;
    private final OutbreakAlertRepository alertRepository;

    // Baseline parameters (historical hourly expected means & standard deviations)
    private static final Map<String, Baseline> BASELINES = Map.of(
            "RESPIRATORY_FEBRILE", new Baseline(3.0, 1.2),
            "GASTROINTESTINAL", new Baseline(2.5, 1.1),
            "NEURO_RASH", new Baseline(0.8, 0.6),
            "ACUTE_CARDIAC", new Baseline(2.0, 0.9)
    );

    private record Baseline(double mean, double stdDev) {}

    @Transactional(readOnly = true)
    public List<OutbreakAlertDto> getActiveAlerts() {
        return alertRepository.findByAcknowledgedFalseOrderByCreatedAtDesc().stream()
                .map(OutbreakAlertDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public OutbreakAlertDto acknowledgeAlert(Long alertId) {
        OutbreakAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + alertId));
        alert.setAcknowledged(true);
        return OutbreakAlertDto.fromEntity(alertRepository.save(alert));
    }

    /**
     * Executes syndromic surveillance scan over past 24 hours of triage complaints.
     */
    @Transactional
    public ClusterScanResultDto runSurveillanceScan() {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        List<Visit> recentVisits = visitRepository.findAll().stream()
                .filter(v -> v.getArrivalTime().isAfter(cutoff))
                .collect(Collectors.toList());

        Map<String, List<Long>> syndromeVisits = new LinkedHashMap<>();
        syndromeVisits.put("RESPIRATORY_FEBRILE", new ArrayList<>());
        syndromeVisits.put("GASTROINTESTINAL", new ArrayList<>());
        syndromeVisits.put("NEURO_RASH", new ArrayList<>());
        syndromeVisits.put("ACUTE_CARDIAC", new ArrayList<>());

        for (Visit visit : recentVisits) {
            String complaint = visit.getChiefComplaint().toLowerCase();
            if (isRespiratoryFebrile(complaint)) {
                syndromeVisits.get("RESPIRATORY_FEBRILE").add(visit.getId());
            }
            if (isGastrointestinal(complaint)) {
                syndromeVisits.get("GASTROINTESTINAL").add(visit.getId());
            }
            if (isNeuroRash(complaint)) {
                syndromeVisits.get("NEURO_RASH").add(visit.getId());
            }
            if (isCardiac(complaint)) {
                syndromeVisits.get("ACUTE_CARDIAC").add(visit.getId());
            }
        }

        List<OutbreakAlertDto> generatedAlerts = new ArrayList<>();
        Map<String, Integer> distribution = new LinkedHashMap<>();

        for (Map.Entry<String, List<Long>> entry : syndromeVisits.entrySet()) {
            String syndrome = entry.getKey();
            List<Long> visitIds = entry.getValue();
            int count = visitIds.size();
            distribution.put(syndrome, count);

            Baseline base = BASELINES.getOrDefault(syndrome, new Baseline(2.0, 1.0));
            double zScore = (count - base.mean()) / base.stdDev();

            AlertLevel level = null;
            if (zScore >= 2.58) {
                level = AlertLevel.OUTBREAK_TRIGGER;
            } else if (zScore >= 1.96) {
                level = AlertLevel.WARNING;
            } else if (zScore >= 1.44) {
                level = AlertLevel.WATCH;
            }

            if (level != null) {
                log.warn("Syndromic Outbreak Cluster Detected! Syndrome={}, Count={}, Z-Score={:.2f}, Level={}",
                        syndrome, count, zScore, level);

                String sampleIds = visitIds.stream().limit(10).map(String::valueOf).collect(Collectors.joining(","));

                OutbreakAlert alert = OutbreakAlert.builder()
                        .syndromeCluster(syndrome)
                        .detectedCount(count)
                        .baselineThreshold(base.mean())
                        .zScore(Math.round(zScore * 100.0) / 100.0)
                        .alertLevel(level)
                        .sampleVisitIds(sampleIds)
                        .acknowledged(false)
                        .createdAt(Instant.now())
                        .build();

                generatedAlerts.add(OutbreakAlertDto.fromEntity(alertRepository.save(alert)));
            }
        }

        String summary = String.format("Scanned %d patient visits in last 24h. Generated %d cluster alert(s).",
                recentVisits.size(), generatedAlerts.size());

        return ClusterScanResultDto.builder()
                .totalVisitsScanned(recentVisits.size())
                .syndromeDistribution(distribution)
                .generatedAlerts(generatedAlerts)
                .scanSummary(summary)
                .build();
    }

    @Scheduled(fixedRate = 7200000) // Run scan every 2 hours
    public void scheduledSentinelScan() {
        log.info("Starting automated Outbreak Sentinel syndromic scan...");
        runSurveillanceScan();
    }

    private boolean isRespiratoryFebrile(String text) {
        return text.contains("fever") || text.contains("cough") || text.contains("breath")
               || text.contains("dyspnea") || text.contains("flu") || text.contains("throat") || text.contains("wheez");
    }

    private boolean isGastrointestinal(String text) {
        return text.contains("vomit") || text.contains("diarrhea") || text.contains("nausea")
               || text.contains("abdominal") || text.contains("cramp") || text.contains("gastro");
    }

    private boolean isNeuroRash(String text) {
        return text.contains("rash") || text.contains("petechiae") || text.contains("stiff neck")
               || text.contains("photophobia") || text.contains("mening") || text.contains("confusion");
    }

    private boolean isCardiac(String text) {
        return text.contains("chest pain") || text.contains("angina") || text.contains("palpitation")
               || text.contains("diaphoresis") || text.contains("pressure");
    }
}
