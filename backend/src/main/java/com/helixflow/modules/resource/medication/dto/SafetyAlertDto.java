package com.helixflow.modules.resource.medication.dto;

import com.helixflow.modules.resource.medication.entity.InteractionSeverity;
import com.helixflow.modules.resource.medication.entity.MedicationInteractionRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyAlertDto {

    private Long primaryMedId;
    private String primaryMedName;
    private Long interactingMedId;
    private String interactingMedName;
    private InteractionSeverity severity;
    private String clinicalEffect;
    private String managementAdvice;

    public static SafetyAlertDto fromEntity(MedicationInteractionRule rule) {
        return SafetyAlertDto.builder()
                .primaryMedId(rule.getPrimaryMed().getId())
                .primaryMedName(rule.getPrimaryMed().getGenericName())
                .interactingMedId(rule.getInteractingMed().getId())
                .interactingMedName(rule.getInteractingMed().getGenericName())
                .severity(rule.getSeverity())
                .clinicalEffect(rule.getClinicalEffect())
                .managementAdvice(rule.getManagementAdvice())
                .build();
    }
}
