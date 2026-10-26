package com.helixflow.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatIfSimulationResult {

    private int deltaArrivalRatePercent;
    private int deltaStaffingPercent;
    private int closedBedsCount;
    private int simulationHours;

    private double overallBedDeficitProbability;
    private int peakQueueDepth;
    private double projectedAverageWaitMinutes;
    private String systemStressLevel; // 'OPTIMAL', 'STRAINED', 'SATURATED', 'COLLAPSED'

    private List<HourlyProjection> hourlyProjections;
    private List<String> mitigationRecommendations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourlyProjection {
        private int hour;
        private int simulatedArrivals;
        private int queueBacklog;
        private int bedsOccupied;
        private int bedsAvailable;
        private double estimatedWaitTimeMinutes;
        private boolean deficitRisk;
    }
}
