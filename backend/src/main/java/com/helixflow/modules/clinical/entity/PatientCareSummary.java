package com.helixflow.modules.clinical.entity;

import com.helixflow.modules.patient.entity.Visit;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "patient_care_summaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientCareSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinical_note_id", nullable = false, unique = true)
    private ClinicalNote clinicalNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @Column(name = "simplified_diagnosis", nullable = false, columnDefinition = "TEXT")
    private String simplifiedDiagnosis;

    @Column(name = "medication_instructions", nullable = false, columnDefinition = "LONGTEXT")
    private String medicationInstructions; // JSON array of medication schedules and precautions

    @Column(name = "home_care_instructions", nullable = false, columnDefinition = "LONGTEXT")
    private String homeCareInstructions; // JSON array of home care and activity instructions

    @Column(name = "red_flag_warnings", nullable = false, columnDefinition = "LONGTEXT")
    private String redFlagWarnings; // JSON array of critical emergency return symptoms

    @Column(name = "follow_up_instructions", nullable = false, columnDefinition = "TEXT")
    private String followUpInstructions;

    @Column(name = "signed_by_doctor_name", nullable = false, length = 100)
    private String signedByDoctorName;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
