package com.helixflow.modules.resource.bed.dto;

import com.helixflow.modules.resource.bed.entity.BedAdmission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BedAdmissionDto {

    private Long id;
    private Long bedId;
    private String bedNumber;
    private String wardCode;
    private Long visitId;
    private String visitNumber;
    private String patientName;
    private Instant admittedAt;
    private Instant dischargedAt;
    private String notes;

    public static BedAdmissionDto fromEntity(BedAdmission admission) {
        return BedAdmissionDto.builder()
                .id(admission.getId())
                .bedId(admission.getBed().getId())
                .bedNumber(admission.getBed().getBedNumber())
                .wardCode(admission.getBed().getWard().getCode())
                .visitId(admission.getVisit().getId())
                .visitNumber(admission.getVisit().getVisitNumber())
                .patientName(admission.getVisit().getPatient().getFirstName() + " " + admission.getVisit().getPatient().getLastName())
                .admittedAt(admission.getAdmittedAt())
                .dischargedAt(admission.getDischargedAt())
                .notes(admission.getNotes())
                .build();
    }
}
