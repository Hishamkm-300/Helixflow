package com.helixflow.modules.clinical.entity;

import com.helixflow.modules.auth.entity.User;
import com.helixflow.modules.patient.entity.Visit;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "clinical_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClinicalNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "raw_text", nullable = false, columnDefinition = "LONGTEXT")
    private String rawText;

    @Builder.Default
    @Column(name = "note_type", nullable = false, length = 30)
    private String noteType = "PROGRESS_NOTE";

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "DRAFT"; // DRAFT, AI_PROCESSED, CLINICIAN_SIGNED

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "signed_at")
    private Instant signedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signed_by_id")
    private User signedBy;

    @OneToOne(mappedBy = "clinicalNote", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private SoapOutput soapOutput;

    @OneToOne(mappedBy = "clinicalNote", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private PatientCareSummary patientCareSummary;
}
