package com.helixflow.modules.resource.bed.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WardBedSummaryDto {

    private Long wardId;
    private String wardCode;
    private String wardName;
    private String department;
    private long totalBeds;
    private long availableBeds;
    private long occupiedBeds;
    private long cleaningBeds;
    private long maintenanceBeds;
    private double occupancyRate;
}
