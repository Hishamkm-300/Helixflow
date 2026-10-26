package com.helixflow.modules.resource.medication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicationPrescribeRequest {

    @NotNull(message = "Visit ID is required")
    private Long visitId;

    @NotNull(message = "Medication ID is required")
    private Long medicationId;

    private String dose;

    private Double numericDose;

    @NotBlank(message = "Route is required (e.g. 'ORAL', 'IV')")
    private String route;

    @NotBlank(message = "Frequency is required (e.g. 'BID', 'DAILY', 'PRN')")
    private String frequency;

    private boolean interactionOverridden;
    private String overrideReason;
}
