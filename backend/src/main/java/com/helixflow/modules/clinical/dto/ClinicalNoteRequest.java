package com.helixflow.modules.clinical.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalNoteRequest {
    private Long visitId;

    @NotBlank(message = "Raw clinical narrative cannot be empty")
    private String rawClinicalNotes;
}
