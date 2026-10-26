package com.helixflow.modules.resource.medication.entity;

import com.helixflow.modules.auth.entity.User;
import com.helixflow.modules.patient.entity.Visit;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "medication_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicationOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescribed_by", nullable = false)
    private User prescribedBy;

    @Column(nullable = false, length = 50)
    private String dose;

    @Column(nullable = false, length = 30)
    private String route;

    @Column(nullable = false, length = 30)
    private String frequency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.ACTIVE;

    @Builder.Default
    @Column(name = "interaction_overridden", nullable = false)
    private boolean interactionOverridden = false;

    @Column(name = "override_reason", columnDefinition = "TEXT")
    private String overrideReason;

    @Column(name = "discontinued_at")
    private Instant discontinuedAt;

    @Column(name = "discontinue_reason", columnDefinition = "TEXT")
    private String discontinueReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discontinued_by")
    private User discontinuedBy;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
