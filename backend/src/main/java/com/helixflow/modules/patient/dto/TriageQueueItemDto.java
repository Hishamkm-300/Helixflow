package com.helixflow.modules.patient.dto;

import com.helixflow.modules.patient.entity.TriageAssessment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageQueueItemDto {

    private Long id;
    private Long triageId;
    private Long visitId;
    private String visitNumber;
    private Long patientId;
    private String mrn;
    private String patientMrn;
    private String patientName;
    private Integer esiLevel;
    private String chiefComplaint;
    private Integer heartRate;
    private Integer bloodPressureSys;
    private Integer bloodPressureDia;
    private Integer respiratoryRate;
    private Double temperatureCelsius;
    private Integer o2Saturation;
    private String consciousnessLevel;
    private String triageNotes;
    private String triagedByName;
    private Instant arrivalTime;
    private Instant triageTime;
    private long waitingMinutes;

    public static TriageQueueItemDto fromEntity(TriageAssessment triage) {
        long waitMinutes = java.time.Duration.between(triage.getVisit().getArrivalTime(), Instant.now()).toMinutes();
        String nurseName = triage.getTriagedBy() != null ? triage.getTriagedBy().getFullName() : "Triage Nurse";
        String mrn = triage.getVisit().getPatient() != null ? triage.getVisit().getPatient().getMrn() : "";

        return TriageQueueItemDto.builder()
                .id(triage.getId())
                .triageId(triage.getId())
                .visitId(triage.getVisit().getId())
                .visitNumber(triage.getVisit().getVisitNumber())
                .patientId(triage.getVisit().getPatient() != null ? triage.getVisit().getPatient().getId() : null)
                .mrn(mrn)
                .patientMrn(mrn)
                .patientName(triage.getVisit().getPatient() != null
                        ? (triage.getVisit().getPatient().getFirstName() + " " + triage.getVisit().getPatient().getLastName()).trim()
                        : "Unknown Patient")
                .esiLevel(triage.getEsiLevel())
                .chiefComplaint(triage.getVisit().getChiefComplaint())
                .heartRate(triage.getHeartRate())
                .bloodPressureSys(triage.getBloodPressureSys())
                .bloodPressureDia(triage.getBloodPressureDia())
                .respiratoryRate(triage.getRespiratoryRate())
                .temperatureCelsius(triage.getTemperatureCelsius())
                .o2Saturation(triage.getO2Saturation())
                .consciousnessLevel(triage.getConsciousnessLevel())
                .triageNotes(triage.getTriageNotes())
                .triagedByName(nurseName)
                .arrivalTime(triage.getVisit().getArrivalTime())
                .triageTime(triage.getTriageTime())
                .waitingMinutes(waitMinutes)
                .build();
    }
}
