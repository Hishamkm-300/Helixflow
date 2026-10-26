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
public class BedDemandForecastDto {

    private LocalDateTime targetTime;
    private String wardCode;
    private String wardName;
    private int currentOccupied;
    private int currentAvailable;
    private int predictedAdmissionsNext24h;
    private int netBedBalance; // available - predicted
    private String shortageRiskLevel; // 'LOW', 'MODERATE', 'CRITICAL'
    private int confidenceIntervalLower;
    private int confidenceIntervalUpper;
}
