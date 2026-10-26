package com.helixflow.modules.clinical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalSoapResponseDto {
    private String subjective;
    private String objective;
    private String assessment;
    private String plan;
    private String triageCategoryDraft;
    @Builder.Default
    private List<String> differentialDiagnoses = new ArrayList<>();
    @Builder.Default
    private List<String> suggestedInvestigations = new ArrayList<>();
    @Builder.Default
    private List<String> suggestedInterventions = new ArrayList<>();
    private boolean needsClinicianReview;
    private String safetyNotice;
}
