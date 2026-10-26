package com.helixflow.modules.resource.medication.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "medication_interactions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_interaction_pair", columnNames = {"primary_med_id", "interacting_med_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicationInteractionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "primary_med_id", nullable = false)
    private Medication primaryMed;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "interacting_med_id", nullable = false)
    private Medication interactingMed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InteractionSeverity severity;

    @Column(name = "clinical_effect", nullable = false, columnDefinition = "TEXT")
    private String clinicalEffect;

    @Column(name = "management_advice", nullable = false, columnDefinition = "TEXT")
    private String managementAdvice;
}
