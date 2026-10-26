package com.helixflow.modules.clinical.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "soap_outputs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoapOutput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinical_note_id", nullable = false, unique = true)
    private ClinicalNote clinicalNote;

    @Column(name = "subjective_json", columnDefinition = "LONGTEXT")
    private String subjectiveJson;

    @Column(name = "objective_json", columnDefinition = "LONGTEXT")
    private String objectiveJson;

    @Column(name = "assessment_json", columnDefinition = "LONGTEXT")
    private String assessmentJson;

    @Column(name = "plan_json", columnDefinition = "LONGTEXT")
    private String planJson;

    @Column(name = "needs_clinician_review", columnDefinition = "LONGTEXT")
    private String needsClinicianReview;

    @Builder.Default
    @Column(name = "model_version", length = 50)
    private String modelVersion = "grok-2";

    @Builder.Default
    @Column(name = "is_fallback", nullable = false)
    private boolean isFallback = false;

    @Column(name = "clinician_edited_json", columnDefinition = "LONGTEXT")
    private String clinicianEditedJson;

    @Column(name = "patient_summary_json", columnDefinition = "LONGTEXT")
    private String patientSummaryJson;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
