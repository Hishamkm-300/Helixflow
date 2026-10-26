package com.helixflow.modules.resource.outbreak.dto;

import com.helixflow.modules.resource.outbreak.entity.AlertLevel;
import com.helixflow.modules.resource.outbreak.entity.OutbreakAlert;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutbreakAlertDto {

    private Long id;
    private String syndromeCluster;
    private Integer detectedCount;
    private Double baselineThreshold;
    private Double zScore;
    private AlertLevel alertLevel;
    private String sampleVisitIds;
    private boolean acknowledged;
    private Instant createdAt;

    public static OutbreakAlertDto fromEntity(OutbreakAlert alert) {
        return OutbreakAlertDto.builder()
                .id(alert.getId())
                .syndromeCluster(alert.getSyndromeCluster())
                .detectedCount(alert.getDetectedCount())
                .baselineThreshold(alert.getBaselineThreshold())
                .zScore(alert.getZScore())
                .alertLevel(alert.getAlertLevel())
                .sampleVisitIds(alert.getSampleVisitIds())
                .acknowledged(alert.isAcknowledged())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}
