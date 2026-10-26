package com.helixflow.modules.resource.medication.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyCheckResultDto {

    private boolean safe;
    private boolean requiresOverride;
    private boolean hardBlocked;
    private List<SafetyAlertDto> alerts;
    private String summaryMessage;
}
