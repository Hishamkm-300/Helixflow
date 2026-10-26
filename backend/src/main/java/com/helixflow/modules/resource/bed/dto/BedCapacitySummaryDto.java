package com.helixflow.modules.resource.bed.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BedCapacitySummaryDto {

    private long totalBeds;
    private long totalHospitalBeds;
    private long availableBeds;
    private long occupiedBeds;
    private long cleaningBeds;
    private long maintenanceBeds;
    private double occupancyRate;
    private double overallOccupancyRate;
    
    @Builder.Default
    private List<WardBedSummaryDto> wardSummaries = new ArrayList<>();
    
    @Builder.Default
    private Map<String, Long> occupiedByWard = new HashMap<>();
    
    @Builder.Default
    private Map<String, Long> availableByWard = new HashMap<>();
}
