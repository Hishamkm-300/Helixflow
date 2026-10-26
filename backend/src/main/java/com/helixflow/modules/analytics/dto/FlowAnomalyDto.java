package com.helixflow.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowAnomalyDto {

    private String id;
    private Long visitId;
    private String visitNumber;
    private String patientName;
    private String anomalyType; // 'EXTENDED_ED_DWELL', 'TRIAGE_BOTTLENECK', 'ACUTE_SURGE'
    private double anomalyScore;
    private String severity; // 'LOW', 'MEDIUM', 'HIGH'
    private Instant detectedAt;
    private String description;
}
