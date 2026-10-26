package com.helixflow.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CongestionForecastDto {

    private LocalDateTime targetTime;
    private double estimatedWaitMinutes;
    private int predictedQueueDepth;
    private String severityLevel; // 'LOW', 'NORMAL', 'HIGH', 'CRITICAL'
    private String modelSource;   // 'SMILE_RANDOM_FOREST' or 'RULE_BASED_FALLBACK'
}
