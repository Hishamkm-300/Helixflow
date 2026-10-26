package com.helixflow.modules.resource.bed.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "beds",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_ward_bed", columnNames = {"ward_id", "bed_number"})
    },
    indexes = {
        @Index(name = "idx_beds_ward_status", columnList = "ward_id, status"),
        @Index(name = "idx_beds_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ward_id", nullable = false)
    private Ward ward;

    @Column(name = "bed_number", nullable = false, length = 20)
    private String bedNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "bed_type", nullable = false, length = 30)
    private BedType bedType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BedStatus status;

    @Version
    @Builder.Default
    private Long version = 0L;
}
