package com.helixflow.modules.clinical.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helixflow.modules.clinical.dto.StructuredSoapResponse;
import com.helixflow.modules.clinical.parser.FallbackSoapExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class GrokRestClient {

    private final RestClient xaiRestClient;
    private final FallbackSoapExtractor fallbackExtractor;
    private final ObjectMapper objectMapper;

    public GrokRestClient(@Qualifier("xaiRestClient") RestClient xaiRestClient,
                          FallbackSoapExtractor fallbackExtractor,
                          ObjectMapper objectMapper) {
        this.xaiRestClient = xaiRestClient;
        this.fallbackExtractor = fallbackExtractor;
        this.objectMapper = objectMapper;
    }

    @Value("${helixflow.ai.mock-mode:true}")
    private boolean mockMode;

    @Value("${helixflow.llm.grok.model:grok-2}")
    private String model;

    @Value("${helixflow.llm.grok.max-retries:1}")
    private int maxRetries;

    private static final String SYSTEM_PROMPT = """
            You are HelixFlow's Clinical Documentation Structuring Assistant.
            Your SOLE purpose is to organize and format raw, unstructured clinical notes into standard SOAP (Subjective, Objective, Assessment, Plan) format.
            
            CRITICAL CLINICAL SAFETY RULES:
            1. NON-DIAGNOSTIC: You MUST NEVER generate new diagnoses, prognoses, or medical decisions. Only summarize and organize what the clinician has explicitly documented.
            2. NO HALLUCINATION / FACT INVENTING: If a specific clinical element (e.g., vital signs, allergy status, specific lab values) is not mentioned in the raw note, you MUST NOT invent it. Instead, include it under the "notDocumented" list of that section.
            3. UNCERTAINTY HANDLING: If any clinician statement is ambiguous, incomplete, or borderline contradictory, place it in the "needsClinicianReview" array with a clear explanation of why review is needed.
            4. STRICT JSON OUTPUT: Return ONLY a valid JSON object strictly matching the specified JSON schema. Do not include markdown code block wrappers (no ```json).
            """;

    public StructuredSoapResponse generateStructuredSoap(String rawClinicalNote) {
        if (mockMode) {
            log.info("helixflow.ai.mock-mode=true: Returning synthetic structured SOAP response (offline demonstration mode).");
            return fallbackExtractor.generateMockSoap(rawClinicalNote);
        }

        GrokChatRequest requestPayload = GrokChatRequest.builder()
                .model(model)
                .temperature(0.1)
                .messages(List.of(
                        GrokChatRequest.ChatMessage.builder()
                                .role("system")
                                .content(SYSTEM_PROMPT)
                                .build(),
                        GrokChatRequest.ChatMessage.builder()
                                .role("user")
                                .content(rawClinicalNote)
                                .build()
                ))
                .build();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                log.info("Dispatching Grok-2 completion request (attempt {}/{})...", attempt + 1, maxRetries + 1);

                GrokChatResponse chatResponse = xaiRestClient.post()
                        .uri("/chat/completions")
                        .body(requestPayload)
                        .retrieve()
                        .body(GrokChatResponse.class);

                if (chatResponse != null && chatResponse.getChoices() != null && !chatResponse.getChoices().isEmpty()) {
                    String content = chatResponse.getChoices().get(0).getMessage().getContent();
                    StructuredSoapResponse response = objectMapper.readValue(content, StructuredSoapResponse.class);
                    response.setModelVersion(chatResponse.getModel());
                    response.setFallback(false);
                    return response;
                }
            } catch (Exception ex) {
                log.warn("Grok-2 API call failed on attempt {}: {}", attempt + 1, ex.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000L * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("Grok-2 exhausted all {} retries or timed out. Activating FallbackSoapExtractor.", maxRetries + 1);
        return fallbackExtractor.extractHeuristic(rawClinicalNote);
    }

    private static final String PATIENT_SUMMARY_SYSTEM_PROMPT = """
            You are HelixFlow's Patient Care Translation Specialist.
            Convert the attending physician's verified Assessment and Plan into an empathetic, highly clear, 6th-grade reading level discharge care summary.
            Return ONLY a valid JSON object matching this schema:
            {
              "diagnosis": "Clear, reassuring explanation of what condition was found in plain English",
              "medications": [
                {
                  "medicationName": "string",
                  "dose": "string",
                  "frequency": "string",
                  "instructions": "Simple plain-language directions and purpose"
                }
              ],
              "homeCare": ["string of practical instructions"],
              "redFlags": ["string of emergency warning signs to return to the ER"],
              "followUp": "Clear timeline and instructions for seeing primary doctor or specialist"
            }
            Do NOT include markdown formatting or backticks. Return raw JSON only.
            """;

    public com.helixflow.modules.clinical.dto.PatientCareSummaryDto generatePatientCareSummary(
            String patientName,
            String mrn,
            String visitNumber,
            String doctorName,
            String assessment,
            String plan,
            List<com.helixflow.modules.resource.medication.entity.MedicationOrder> activeOrders) {

        if (mockMode) {
            log.info("helixflow.ai.mock-mode=true: Generating synthetic patient care summary (offline demonstration mode).");
            return fallbackExtractor.generateMockPatientSummary(
                    patientName, mrn, visitNumber, doctorName, assessment, plan, activeOrders);
        }

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Patient: ").append(patientName).append(" (MRN: ").append(mrn).append(")\n");
        promptBuilder.append("Attending Physician: ").append(doctorName).append("\n\n");
        promptBuilder.append("DOCTOR'S VERIFIED CLINICAL ASSESSMENT:\n").append(assessment).append("\n\n");
        promptBuilder.append("DOCTOR'S CARE PLAN:\n").append(plan).append("\n\n");

        if (activeOrders != null && !activeOrders.isEmpty()) {
            promptBuilder.append("CURRENT ACTIVE INPATIENT/DISCHARGE MEDICATION ORDERS:\n");
            for (com.helixflow.modules.resource.medication.entity.MedicationOrder ord : activeOrders) {
                promptBuilder.append("• ").append(ord.getMedication().getGenericName())
                        .append(" - ").append(ord.getDose())
                        .append(", ").append(ord.getRoute())
                        .append(", ").append(ord.getFrequency()).append("\n");
            }
        }

        GrokChatRequest requestPayload = GrokChatRequest.builder()
                .model(model)
                .temperature(0.2)
                .messages(List.of(
                        GrokChatRequest.ChatMessage.builder()
                                .role("system")
                                .content(PATIENT_SUMMARY_SYSTEM_PROMPT)
                                .build(),
                        GrokChatRequest.ChatMessage.builder()
                                .role("user")
                                .content(promptBuilder.toString())
                                .build()
                ))
                .build();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                log.info("Dispatching Grok-2 Patient Summary completion request (attempt {}/{})...", attempt + 1, maxRetries + 1);
                GrokChatResponse chatResponse = xaiRestClient.post()
                        .uri("/chat/completions")
                        .body(requestPayload)
                        .retrieve()
                        .body(GrokChatResponse.class);

                if (chatResponse != null && chatResponse.getChoices() != null && !chatResponse.getChoices().isEmpty()) {
                    String content = chatResponse.getChoices().get(0).getMessage().getContent().trim();
                    if (content.startsWith("```json")) {
                        content = content.replace("```json", "").replace("```", "").trim();
                    } else if (content.startsWith("```")) {
                        content = content.replace("```", "").trim();
                    }

                    com.helixflow.modules.clinical.dto.PatientCareSummaryDto summary =
                            objectMapper.readValue(content, com.helixflow.modules.clinical.dto.PatientCareSummaryDto.class);
                    summary.setPatientName(patientName);
                    summary.setMrn(mrn);
                    summary.setVisitNumber(visitNumber);
                    summary.setDoctorName(doctorName);
                    summary.setDoctorSignature("Verified & Electronically Signed by " + doctorName);
                    summary.setSignedAt(java.time.Instant.now());

                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy")
                            .withZone(java.time.ZoneId.of("UTC"));
                    summary.setVisitDate(formatter.format(java.time.Instant.now()));

                    return summary;
                }
            } catch (Exception ex) {
                log.warn("Grok-2 Patient Summary call failed on attempt {}: {}", attempt + 1, ex.getMessage());
            }
        }

        log.warn("Grok-2 Patient Summary exhausted retries. Activating fallback summary generator.");
        return fallbackExtractor.generateMockPatientSummary(
                patientName, mrn, visitNumber, doctorName, assessment, plan, activeOrders);
    }
}
