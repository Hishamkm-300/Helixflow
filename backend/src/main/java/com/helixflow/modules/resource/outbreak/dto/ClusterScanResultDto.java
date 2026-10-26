package com.helixflow.modules.resource.outbreak.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterScanResultDto {

    private int totalVisitsScanned;
    private Map<String, Integer> syndromeDistribution;
    private List<OutbreakAlertDto> generatedAlerts;
    private String scanSummary;
}
