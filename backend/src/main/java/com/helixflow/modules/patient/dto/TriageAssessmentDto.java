package com.helixflow.modules.patient.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageAssessmentDto {

    @NotNull(message = "Visit ID is required")
    private Long visitId;

    @NotNull(message = "ESI Level is required")
    @Min(value = 1, message = "ESI level must be between 1 (Immediate) and 5 (Non-urgent)")
    @Max(value = 5, message = "ESI level must be between 1 (Immediate) and 5 (Non-urgent)")
    private Integer esiLevel;

    private Integer heartRate;
    private Integer bloodPressureSys;
    private Integer bloodPressureDia;
    private Integer respiratoryRate;
    private Double temperatureCelsius;
    private Integer o2Saturation;
    private String consciousnessLevel;
    private String triageNotes;
    private Integer painScore;
    private String symptomDuration;
}
