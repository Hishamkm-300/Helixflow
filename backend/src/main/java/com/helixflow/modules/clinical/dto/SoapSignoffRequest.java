package com.helixflow.modules.clinical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoapSignoffRequest {
    private String subjective;
    private String objective;
    private String assessment;
    private String plan;
    private String triageCategory;
    private List<String> differentialDiagnoses;
    private List<String> suggestedInvestigations;
    private List<String> suggestedInterventions;
}
