package com.helixflow.modules.resource.medication.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionRequestDto {

    @NotNull(message = "Visit ID is required")
    private Long visitId;

    @NotNull(message = "Medication ID is required")
    private Long medicationId;

    private String dose;
    private Double numericDose;

    @Builder.Default
    private String route = "ORAL";

    @Builder.Default
    private String frequency = "ONCE_DAILY";

    private boolean interactionOverridden;
    private String overrideReason;

    public MedicationPrescribeRequest toPrescribeRequest() {
        return MedicationPrescribeRequest.builder()
                .visitId(visitId)
                .medicationId(medicationId)
                .dose(dose != null ? dose : (numericDose != null ? String.valueOf(numericDose) : null))
                .numericDose(numericDose)
                .route(route != null ? route : "ORAL")
                .frequency(frequency != null ? frequency : "ONCE_DAILY")
                .interactionOverridden(interactionOverridden)
                .overrideReason(overrideReason)
                .build();
    }
}
