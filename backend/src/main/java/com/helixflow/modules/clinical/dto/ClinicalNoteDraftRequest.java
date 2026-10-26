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
public class ClinicalNoteDraftRequest {
    @NotBlank(message = "Raw clinical note text cannot be blank")
    private String rawText;
    private String noteType;
}
