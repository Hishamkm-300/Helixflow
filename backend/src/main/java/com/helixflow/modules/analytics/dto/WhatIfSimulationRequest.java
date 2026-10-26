package com.helixflow.modules.analytics.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatIfSimulationRequest {

    @Builder.Default
    @Min(value = -50, message = "Arrival rate delta cannot be less than -50%")
    @Max(value = 150, message = "Arrival rate delta cannot exceed +150%")
    private int deltaArrivalRatePercent = 20; // e.g. +20% surge

    @Builder.Default
    @Min(value = -50, message = "Staffing delta cannot be less than -50%")
    @Max(value = 50, message = "Staffing delta cannot exceed +50%")
    private int deltaStaffingPercent = 0; // e.g. -15% staff shortage

    @Builder.Default
    @Min(value = 0, message = "Closed beds count cannot be negative")
    private int closedBedsCount = 0; // e.g. 3 beds closed for maintenance

    @Builder.Default
    @Min(value = 6, message = "Simulation horizon must be at least 6 hours")
    @Max(value = 72, message = "Simulation horizon cannot exceed 72 hours")
    private int simulationHours = 24;
}
