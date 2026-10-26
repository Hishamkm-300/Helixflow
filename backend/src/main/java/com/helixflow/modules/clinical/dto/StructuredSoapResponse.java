package com.helixflow.modules.clinical.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StructuredSoapResponse {

    private Subjective subjective;
    private Objective objective;
    private Assessment assessment;
    private Plan plan;

    private List<String> needsClinicianReview;
    private String modelVersion;
    private boolean isFallback;

    @Builder.Default
    private String safetyDisclaimer = "AI-assisted draft for administrative structuring. Clinician must review, edit, and sign off before inclusion in medical record.";

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subjective {
        private String chiefComplaint;
        private String historyOfPresentIllness;
        private String reviewOfSystems;
        private List<String> notDocumented;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Objective {
        private Map<String, String> vitalSigns;
        private String physicalExam;
        private String labsAndDiagnostics;
        private List<String> notDocumented;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Assessment {
        private String summary;
        private List<String> clinicalImpressionStatedByClinician;
        private List<String> uncertaintiesNeedingReview;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Plan {
        private List<String> medicationsOrdered;
        private List<String> diagnosticOrders;
        private List<String> consultations;
        private String patientInstructions;
        private String followUp;
    }
}
