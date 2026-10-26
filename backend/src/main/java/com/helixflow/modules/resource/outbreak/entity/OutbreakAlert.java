package com.helixflow.modules.resource.outbreak.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "outbreak_alerts",
    indexes = {
        @Index(name = "idx_outbreak_time", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutbreakAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "syndrome_cluster", nullable = false, length = 100)
    private String syndromeCluster;

    @Column(name = "detected_count", nullable = false)
    private Integer detectedCount;

    @Column(name = "baseline_threshold", nullable = false)
    private Double baselineThreshold;

    @Column(name = "z_score", nullable = false)
    private Double zScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_level", nullable = false, length = 20)
    private AlertLevel alertLevel;

    @Column(name = "sample_visit_ids", columnDefinition = "TEXT")
    private String sampleVisitIds;

    @Builder.Default
    @Column(name = "is_acknowledged", nullable = false)
    private boolean acknowledged = false;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
