package com.helixflow.modules.resource.bed.entity;

import com.helixflow.modules.patient.entity.Visit;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "bed_admissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BedAdmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bed_id", nullable = false)
    private Bed bed;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @Builder.Default
    @Column(name = "admitted_at", nullable = false)
    private Instant admittedAt = Instant.now();

    @Column(name = "discharged_at")
    private Instant dischargedAt;

    @Column(length = 255)
    private String notes;
}
