package com.helixflow.modules.patient.entity;

import com.helixflow.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "triage_assessments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TriageAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triaged_by", nullable = false)
    private User triagedBy;

    @Column(name = "esi_level", nullable = false)
    private Integer esiLevel; // 1 (Immediate) to 5 (Non-urgent)

    @Column(name = "heart_rate")
    private Integer heartRate;

    @Column(name = "blood_pressure_sys")
    private Integer bloodPressureSys;

    @Column(name = "blood_pressure_dia")
    private Integer bloodPressureDia;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "temperature_celsius")
    private Double temperatureCelsius;

    @Column(name = "o2_saturation")
    private Integer o2Saturation;

    @Builder.Default
    @Column(name = "consciousness_level", nullable = false, length = 20)
    private String consciousnessLevel = "ALERT";

    @Column(name = "triage_notes", columnDefinition = "TEXT")
    private String triageNotes;

    @Column(name = "pain_score")
    private Integer painScore;

    @Column(name = "symptom_duration", length = 64)
    private String symptomDuration;

    @Builder.Default
    @Column(name = "triage_time", nullable = false)
    private Instant triageTime = Instant.now();
}
