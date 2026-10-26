package com.helixflow.modules.resource.medication.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicationSafetyCheckRequest {

    private Long visitId;

    @NotEmpty(message = "At least one medication ID is required for safety evaluation")
    private List<Long> proposedMedicationIds;
}
