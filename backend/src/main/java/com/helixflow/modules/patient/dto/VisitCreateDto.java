package com.helixflow.modules.patient.dto;

import com.helixflow.modules.patient.entity.VisitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitCreateDto {

    @NotNull(message = "Patient ID is required")
    private Long patientId;

    @NotNull(message = "Visit type is required")
    private VisitType visitType;

    @NotBlank(message = "Chief complaint is required")
    private String chiefComplaint;
}
