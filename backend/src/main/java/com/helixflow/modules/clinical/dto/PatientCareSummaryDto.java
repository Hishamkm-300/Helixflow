package com.helixflow.modules.clinical.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PatientCareSummaryDto {

    private String diagnosis;
    private List<PatientMedicationInstructionDto> medications;
    private List<String> homeCare;
    private List<String> redFlags;
    private String followUp;

    private String doctorSignature;
    private String doctorName;
    private Instant signedAt;

    private String patientName;
    private String mrn;
    private String visitNumber;
    private String visitDate;
}
