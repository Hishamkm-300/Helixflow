package com.helixflow.modules.clinical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientMedicationInstructionDto {

    private String medicationName;
    private String dose;
    private String frequency;
    private String instructions;
}
