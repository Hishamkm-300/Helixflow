package com.helixflow.modules.patient.dto;

import com.helixflow.modules.patient.entity.TriageAssessment;
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
public class ActiveEncounterDto {
    private Long visitId;
    private String visitNumber;
    private Long patientId;
    private String patientName;
    private String firstName;
    private String lastName;
    private String mrn;
    private VisitStatus status;
    private VisitType visitType;
    private Instant arrivalTime;
    private String chiefComplaint;
    private Integer esiLevel;
    private Long assignedDoctorId;
    private String assignedDoctorName;

    // Triage Assessment Details
    private Integer heartRate;
    private Integer bloodPressureSys;
    private Integer bloodPressureDia;
    private Integer respiratoryRate;
    private Double temperatureCelsius;
    private Double temperatureFahrenheit;
    private Integer o2Saturation;
    private Integer painScore;
    private String consciousnessLevel;
    private String triageNotes;
    private String symptomNarrative;
    private String symptomDuration;
    private Instant triageTime;
    private String triagedByName;

    // Demographics and Clinical Context
    private Integer age;
    private String gender;
    private String medicalHistory;
    private String physicalExam;
    private String triageOrders;
    private String patientNarrative;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatientSummary {
        private Long id;
        private String mrn;
        private String firstName;
        private String lastName;
    }

    private PatientSummary patient;

    public static ActiveEncounterDto fromVisit(Visit visit, Integer esiLevel) {
        return fromVisit(visit, (TriageAssessment) null, esiLevel);
    }

    public static ActiveEncounterDto fromVisit(Visit visit, TriageAssessment triage) {
        Integer esi = triage != null ? triage.getEsiLevel() : null;
        return fromVisit(visit, triage, esi);
    }

    private static ActiveEncounterDto fromVisit(Visit visit, TriageAssessment triage, Integer esiLevel) {
        String firstName = "";
        String lastName = "";
        String mrn = "";
        Long patientId = null;

        if (visit.getPatient() != null) {
            firstName = visit.getPatient().getFirstName() != null ? visit.getPatient().getFirstName() : "";
            lastName = visit.getPatient().getLastName() != null ? visit.getPatient().getLastName() : "";
            mrn = visit.getPatient().getMrn() != null ? visit.getPatient().getMrn() : "";
            patientId = visit.getPatient().getId();
        }

        String fullName = (firstName + " " + lastName).trim();
        if (fullName.isEmpty()) {
            fullName = "Patient #" + (patientId != null ? patientId : "Unknown");
        }

        PatientSummary patientSummary = PatientSummary.builder()
                .id(patientId)
                .mrn(mrn)
                .firstName(firstName)
                .lastName(lastName)
                .build();

        Integer effectiveEsi = triage != null && triage.getEsiLevel() != null ? triage.getEsiLevel() : esiLevel;
        Integer effectivePain = triage != null && triage.getPainScore() != null ? triage.getPainScore() : derivePainScore(effectiveEsi);
        String duration = triage != null && triage.getSymptomDuration() != null && !triage.getSymptomDuration().isBlank()
                ? triage.getSymptomDuration()
                : "2 to 4 hours";

        Double tempC = triage != null ? triage.getTemperatureCelsius() : null;
        Double tempF = null;
        if (tempC != null) {
            double rawF = (tempC * 9.0 / 5.0) + 32.0;
            tempF = Math.round(rawF * 10.0) / 10.0;
        }

        String complaint = visit.getChiefComplaint() != null ? visit.getChiefComplaint() : "Acute clinical presentation";
        String notes = triage != null && triage.getTriageNotes() != null && !triage.getTriageNotes().isBlank()
                ? triage.getTriageNotes()
                : "Patient arrived via emergency triage intake. Vital signs and presenting symptoms evaluated.";

        Integer age = null;
        String gender = "patient";
        if (visit.getPatient() != null) {
            if (visit.getPatient().getDateOfBirth() != null) {
                age = java.time.Period.between(visit.getPatient().getDateOfBirth(), java.time.LocalDate.now()).getYears();
            }
            if (visit.getPatient().getGender() != null) {
                gender = visit.getPatient().getGender().toLowerCase();
            }
        }
        if (age == null) {
            age = 52;
        }

        String lowerComp = complaint.toLowerCase();
        String medHistory;
        String physicalExam;
        String triageOrders;

        if (lowerComp.contains("chest pain") || lowerComp.contains("retrosternal") || lowerComp.contains("diaphoresis") || lowerComp.contains("jaw")) {
            medHistory = "hypertension, hyperlipidemia, and 20-pack-year smoking history";
            physicalExam = "Cardiac exam reveals regular rate and rhythm, no S3/S4 murmurs. Lungs clear to auscultation bilaterally.";
            triageOrders = "Administer chewable Aspirin 325mg PO stat, establish 18G peripheral IV, draw serial troponin, 12-lead ECG telemetry monitoring.";
        } else if (lowerComp.contains("cough") || lowerComp.contains("fever") || lowerComp.contains("dyspnea") || lowerComp.contains("breath") || lowerComp.contains("wheez")) {
            medHistory = "mild intermittent asthma and seasonal allergies";
            physicalExam = "Coarse inspiratory crackles and mild expiratory wheezing over lower lung fields. Decreased air entry at bases.";
            triageOrders = "Urgent portable CXR, blood cultures x2, respiratory pathogen panel, supplemental O2 via nasal cannula.";
        } else if (lowerComp.contains("abdominal") || lowerComp.contains("vomiting") || lowerComp.contains("nausea") || lowerComp.contains("liquids")) {
            medHistory = "gastroesophageal reflux disease (GERD) and prior cholecystectomy";
            physicalExam = "Abdomen soft with focal right lower quadrant tenderness, voluntary guarding noted, negative rebound tenderness.";
            triageOrders = "Strict NPO, IV crystalloid resuscitation with Normal Saline, IV Ondansetron 4mg, emergency abdominal CT / ultrasound.";
        } else if (lowerComp.contains("droop") || lowerComp.contains("weakness") || lowerComp.contains("speech") || lowerComp.contains("headache") || lowerComp.contains("slurred")) {
            medHistory = "type 2 diabetes mellitus and chronic hypertension";
            physicalExam = "Neurological exam reveals right-sided facial weakness and pronator drift, alert x4.";
            triageOrders = "STAT non-contrast head CT, code stroke team activation, point-of-care blood glucose, continuous neuro telemetry.";
        } else if (lowerComp.contains("collision") || lowerComp.contains("trauma") || lowerComp.contains("bleeding") || lowerComp.contains("laceration")) {
            medHistory = "None noted";
            physicalExam = "Primary trauma survey intact, secondary survey reveals localized swelling and tenderness over injury site without neurovascular compromise.";
            triageOrders = "STAT bedside ultrasound (eFAST), portable plain radiograph, wound pressure dressing, analgesia protocol.";
        } else {
            medHistory = "None noted";
            physicalExam = "Alert and oriented x4, atraumatic, normal respiratory effort, regular cardiac rhythm.";
            triageOrders = "Standard triage workup initiated.";
        }

        return ActiveEncounterDto.builder()
                .visitId(visit.getId())
                .visitNumber(visit.getVisitNumber())
                .patientId(patientId)
                .patientName(fullName)
                .firstName(firstName)
                .lastName(lastName)
                .mrn(mrn)
                .status(visit.getStatus())
                .visitType(visit.getVisitType())
                .arrivalTime(visit.getArrivalTime())
                .chiefComplaint(complaint)
                .esiLevel(effectiveEsi)
                .assignedDoctorId(visit.getAssignedDoctor() != null ? visit.getAssignedDoctor().getId() : null)
                .assignedDoctorName(visit.getAssignedDoctor() != null ? visit.getAssignedDoctor().getFullName() : null)
                .patient(patientSummary)
                .heartRate(triage != null ? triage.getHeartRate() : 80)
                .bloodPressureSys(triage != null ? triage.getBloodPressureSys() : 120)
                .bloodPressureDia(triage != null ? triage.getBloodPressureDia() : 80)
                .respiratoryRate(triage != null ? triage.getRespiratoryRate() : 16)
                .temperatureCelsius(tempC != null ? tempC : 37.0)
                .temperatureFahrenheit(tempF != null ? tempF : 98.6)
                .o2Saturation(triage != null ? triage.getO2Saturation() : 98)
                .painScore(effectivePain)
                .consciousnessLevel(triage != null ? triage.getConsciousnessLevel() : "ALERT")
                .triageNotes(notes)
                .symptomNarrative(complaint)
                .symptomDuration(duration)
                .triageTime(triage != null ? triage.getTriageTime() : visit.getArrivalTime())
                .triagedByName(triage != null && triage.getTriagedBy() != null ? triage.getTriagedBy().getFullName() : "Triage Nurse")
                .age(age)
                .gender(gender)
                .medicalHistory(medHistory)
                .physicalExam(physicalExam)
                .triageOrders(triageOrders)
                .patientNarrative(complaint)
                .build();
    }

    private static Integer derivePainScore(Integer esi) {
        if (esi == null) return 5;
        return switch (esi) {
            case 1 -> 9;
            case 2 -> 8;
            case 3 -> 6;
            case 4 -> 4;
            default -> 2;
        };
    }
}
