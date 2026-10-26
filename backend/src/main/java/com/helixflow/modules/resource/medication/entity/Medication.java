package com.helixflow.modules.resource.medication.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "medications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Medication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "generic_name", nullable = false, length = 100)
    private String genericName;

    @Column(name = "brand_name", length = 100)
    private String brandName;

    @Column(name = "dosage_form", nullable = false, length = 50)
    private String dosageForm;

    @Column(nullable = false, length = 50)
    private String strength;

    @Column(name = "max_single_dose_mg")
    private Double maxSingleDoseMg;

    @Column(length = 20)
    private String unit;
}
