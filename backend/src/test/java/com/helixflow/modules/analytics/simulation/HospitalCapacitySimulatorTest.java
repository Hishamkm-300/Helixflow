package com.helixflow.modules.analytics.simulation;

import com.helixflow.modules.analytics.dto.WhatIfSimulationRequest;
import com.helixflow.modules.analytics.dto.WhatIfSimulationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HospitalCapacitySimulatorTest {

    private HospitalCapacitySimulator simulator;

    @BeforeEach
    void setUp() {
        simulator = new HospitalCapacitySimulator();
    }

    @Test
    @DisplayName("Run baseline simulation produces full hourly projections and valid summary metrics")
    void testRunSimulation_Baseline() {
        WhatIfSimulationRequest request = WhatIfSimulationRequest.builder()
                .simulationHours(24)
                .deltaArrivalRatePercent(0)
                .deltaStaffingPercent(0)
                .closedBedsCount(0)
                .build();

        WhatIfSimulationResult result = simulator.runSimulation(request, 22, 10);

        assertNotNull(result);
        assertEquals(24, result.getHourlyProjections().size());
        assertTrue(result.getPeakQueueDepth() >= 0);
        assertTrue(result.getProjectedAverageWaitMinutes() >= 0);
        assertNotNull(result.getSystemStressLevel());
        assertNotNull(result.getMitigationRecommendations());
    }

    @Test
    @DisplayName("Surge scenario (+50% arrival, -30% staffing, 5 closed beds) escalates stress level and triggers mitigations")
    void testRunSimulation_SurgeScenario() {
        WhatIfSimulationRequest request = WhatIfSimulationRequest.builder()
                .simulationHours(24)
                .deltaArrivalRatePercent(50)
                .deltaStaffingPercent(-30)
                .closedBedsCount(5)
                .build();

        WhatIfSimulationResult result = simulator.runSimulation(request, 22, 16);

        assertNotNull(result);
        assertEquals(24, result.getHourlyProjections().size());
        assertFalse(result.getMitigationRecommendations().isEmpty());
        // Verify closed beds trigger recommendation
        assertTrue(result.getMitigationRecommendations().stream().anyMatch(r -> r.contains("offline bed") || r.contains("rapid response")));
    }
}
