package com.helixflow.modules.analytics.ml;

import com.helixflow.modules.analytics.dto.CongestionForecastDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class FallbackRuleBasedPredictor {

    public CongestionForecastDto predictCongestion(int queueDepth, int highEsiCount, double bedOccupancy) {
        log.warn("Executing FallbackRuleBasedPredictor heuristic (in-JVM ML fallback)");

        // Deterministic operational heuristic:
        // Baseline 14.5 minutes per waiting patient + 22 minutes per acute ESI 1-2 trauma case
        // Scaled by occupancy pressure (>85% occupancy adds a 1.4x bottleneck factor)
        double occupancyFactor = bedOccupancy > 0.85 ? 1.4 : 1.0;
        double estimatedWaitMinutes = ((queueDepth * 14.5) + (highEsiCount * 22.0)) * occupancyFactor;

        String severity;
        if (estimatedWaitMinutes > 120.0) {
            severity = "CRITICAL";
        } else if (estimatedWaitMinutes > 60.0) {
            severity = "HIGH";
        } else if (estimatedWaitMinutes > 30.0) {
            severity = "NORMAL";
        } else {
            severity = "LOW";
        }

        int predictedQueue = queueDepth + (int) Math.round(highEsiCount * 0.7);

        return CongestionForecastDto.builder()
                .targetTime(LocalDateTime.now().plusHours(2))
                .estimatedWaitMinutes(Math.round(estimatedWaitMinutes * 10.0) / 10.0)
                .predictedQueueDepth(predictedQueue)
                .severityLevel(severity)
                .modelSource("RULE_BASED_FALLBACK")
                .build();
    }
}
