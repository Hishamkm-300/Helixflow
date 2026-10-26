package com.helixflow.modules.clinical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientSummaryRequestDto {

    private Long noteId;
    private Long visitId;
    private String assessment;
    private String plan;
}
