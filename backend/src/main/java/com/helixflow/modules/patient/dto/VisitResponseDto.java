package com.helixflow.modules.patient.dto;

import com.helixflow.modules.patient.entity.Visit;
import com.helixflow.modules.patient.entity.VisitStatus;
import com.helixflow.modules.patient.entity.VisitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitResponseDto {

    private Long id;
    private Long patientId;
    private String patientMrn;
    private String patientFullName;
    private String visitNumber;
    private VisitType visitType;
    private VisitStatus status;
    private Instant arrivalTime;
    private Instant dischargeTime;
    private String chiefComplaint;

    public static VisitResponseDto fromEntity(Visit visit) {
        return VisitResponseDto.builder()
                .id(visit.getId())
                .patientId(visit.getPatient().getId())
                .patientMrn(visit.getPatient().getMrn())
                .patientFullName(visit.getPatient().getFirstName() + " " + visit.getPatient().getLastName())
                .visitNumber(visit.getVisitNumber())
                .visitType(visit.getVisitType())
                .status(visit.getStatus())
                .arrivalTime(visit.getArrivalTime())
                .dischargeTime(visit.getDischargeTime())
                .chiefComplaint(visit.getChiefComplaint())
                .build();
    }
}
