package com.helixflow.modules.analytics.simulation;

import com.helixflow.modules.analytics.dto.WhatIfSimulationRequest;
import com.helixflow.modules.analytics.dto.WhatIfSimulationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class HospitalCapacitySimulator {

    // Circadian hourly multiplier for patient arrival rate (00:00 to 23:00)
    private static final double[] CIRCADIAN_WEIGHTS = {
            0.4, 0.3, 0.25, 0.25, 0.3, 0.5, 0.8, 1.1,
            1.3, 1.4, 1.45, 1.4, 1.35, 1.3, 1.4, 1.5,
            1.55, 1.5, 1.4, 1.3, 1.1, 0.9, 0.7, 0.5
    };

    /**
     * Executes discrete-time capacity simulation projecting hourly queues,
     * bed shortages, and system stress under what-if scenario parameters.
     */
    public WhatIfSimulationResult runSimulation(WhatIfSimulationRequest request, int totalBeds, int currentlyOccupied) {
        int hours = request.getSimulationHours();
        double arrivalMultiplier = 1.0 + (request.getDeltaArrivalRatePercent() / 100.0);
        double staffingMultiplier = Math.max(0.2, 1.0 + (request.getDeltaStaffingPercent() / 100.0));
        int effectiveCapacity = Math.max(1, totalBeds - request.getClosedBedsCount());

        double baseArrivalsPerHour = 6.0;
        double baseThroughputPerHour = 5.8 * staffingMultiplier;

        int currentQueue = 3;
        int currentBedsOccupied = Math.min(currentlyOccupied, effectiveCapacity);

        List<WhatIfSimulationResult.HourlyProjection> projections = new ArrayList<>();
        int peakQueue = 0;
        int hoursInDeficit = 0;
        double totalWaitSum = 0;

        for (int h = 1; h <= hours; h++) {
            int hourOfDay = (h - 1) % 24;
            double weight = CIRCADIAN_WEIGHTS[hourOfDay];

            int hourlyArrivals = (int) Math.round(baseArrivalsPerHour * weight * arrivalMultiplier);
            int hourlyProcessed = (int) Math.round(baseThroughputPerHour * (0.8 + 0.4 * Math.random()));

            currentQueue = Math.max(0, currentQueue + hourlyArrivals - hourlyProcessed);
            if (currentQueue > peakQueue) {
                peakQueue = currentQueue;
            }

            // Estimate inpatient admission conversion (approx 20% of processed ED patients require a bed)
            int newAdmissions = (int) Math.round(hourlyProcessed * 0.22);
            int discharges = (int) Math.round((currentBedsOccupied * 0.05) + Math.random());

            currentBedsOccupied = Math.min(effectiveCapacity, Math.max(0, currentBedsOccupied + newAdmissions - discharges));
            int bedsAvailable = Math.max(0, effectiveCapacity - currentBedsOccupied);

            boolean isDeficit = bedsAvailable == 0 && newAdmissions > discharges;
            if (isDeficit) {
                hoursInDeficit++;
            }

            double estWaitTime = (currentQueue * 14.0) * (bedsAvailable == 0 ? 1.5 : 1.0);
            totalWaitSum += estWaitTime;

            projections.add(WhatIfSimulationResult.HourlyProjection.builder()
                    .hour(h)
                    .simulatedArrivals(hourlyArrivals)
                    .queueBacklog(currentQueue)
                    .bedsOccupied(currentBedsOccupied)
                    .bedsAvailable(bedsAvailable)
                    .estimatedWaitTimeMinutes(Math.round(estWaitTime * 10.0) / 10.0)
                    .deficitRisk(isDeficit)
                    .build());
        }

        double deficitProbability = Math.round(((double) hoursInDeficit / hours) * 1000.0) / 10.0;
        double avgWait = Math.round((totalWaitSum / hours) * 10.0) / 10.0;

        String stressLevel;
        if (deficitProbability > 40.0 || peakQueue > 25) {
            stressLevel = "COLLAPSED";
        } else if (deficitProbability > 20.0 || peakQueue > 15) {
            stressLevel = "SATURATED";
        } else if (deficitProbability > 5.0 || peakQueue > 8) {
            stressLevel = "STRAINED";
        } else {
            stressLevel = "OPTIMAL";
        }

        List<String> recommendations = new ArrayList<>();
        if (deficitProbability > 20.0) {
            recommendations.add("Trigger on-call rapid response nursing roster for peak hours 14:00 - 19:00.");
            recommendations.add("Activate expedited morning discharge rounds to free med-surg beds before peak arrival wave.");
        }
        if (request.getClosedBedsCount() > 0) {
            recommendations.add("Prioritize maintenance sanitization to return " + request.getClosedBedsCount() + " offline bed(s) to active service.");
        }
        if (request.getDeltaArrivalRatePercent() >= 30) {
            recommendations.add("Establish dedicated fast-track triage clinic for non-urgent ESI 4-5 presentations.");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("Current hospital operational capacity is sufficient to absorb projected demand.");
        }

        return WhatIfSimulationResult.builder()
                .deltaArrivalRatePercent(request.getDeltaArrivalRatePercent())
                .deltaStaffingPercent(request.getDeltaStaffingPercent())
                .closedBedsCount(request.getClosedBedsCount())
                .simulationHours(hours)
                .overallBedDeficitProbability(deficitProbability)
                .peakQueueDepth(peakQueue)
                .projectedAverageWaitMinutes(avgWait)
                .systemStressLevel(stressLevel)
                .hourlyProjections(projections)
                .mitigationRecommendations(recommendations)
                .build();
    }
}
