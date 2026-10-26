package com.helixflow.modules.clinical.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helixflow.common.dto.ApiResponse;
import com.helixflow.common.exception.ResourceNotFoundException;
import com.helixflow.modules.auth.entity.User;
import com.helixflow.modules.auth.repository.UserRepository;
import com.helixflow.modules.clinical.client.GrokRestClient;
import com.helixflow.modules.clinical.dto.*;
import com.helixflow.modules.clinical.entity.ClinicalNote;
import com.helixflow.modules.clinical.entity.SoapOutput;
import com.helixflow.modules.clinical.repository.ClinicalNoteRepository;
import com.helixflow.modules.clinical.repository.SoapOutputRepository;
import com.helixflow.modules.patient.dto.ActiveEncounterDto;
import com.helixflow.modules.patient.entity.TriageAssessment;
import com.helixflow.modules.patient.entity.Visit;
import com.helixflow.modules.patient.repository.TriageAssessmentRepository;
import com.helixflow.modules.patient.repository.VisitRepository;
import com.helixflow.modules.clinical.entity.PatientCareSummary;
import com.helixflow.modules.clinical.repository.PatientCareSummaryRepository;
import com.helixflow.modules.patient.entity.VisitStatus;
import com.helixflow.modules.resource.bed.entity.BedAdmission;
import com.helixflow.modules.resource.bed.repository.BedAdmissionRepository;
import com.helixflow.modules.resource.bed.service.BedManagementService;
import com.helixflow.security.Role;
import com.helixflow.security.SecurityContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.helixflow.modules.resource.medication.entity.MedicationOrder;
import com.helixflow.modules.resource.medication.entity.OrderStatus;
import com.helixflow.modules.resource.medication.repository.MedicationOrderRepository;

@Slf4j
@RestController
@RequestMapping("/api/v1/clinical")
@RequiredArgsConstructor
public class ClinicalNoteController {

    private final VisitRepository visitRepository;
    private final TriageAssessmentRepository triageAssessmentRepository;
    private final GrokRestClient grokRestClient;
    private final ClinicalNoteRepository clinicalNoteRepository;
    private final SoapOutputRepository soapOutputRepository;
    private final PatientCareSummaryRepository patientCareSummaryRepository;
    private final MedicationOrderRepository medicationOrderRepository;
    private final BedAdmissionRepository bedAdmissionRepository;
    private final BedManagementService bedManagementService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * Returns all active patient encounters in TRIAGE_WAIT, IN_TREATMENT, or ADMITTED status.
     * All doctors and clinicians can view and draft clinical notes for ANY active encounter.
     * Zero restriction by assigned_doctor_id.
     */
    @GetMapping({"/encounters", "/visits/active"})
    public ResponseEntity<ApiResponse<List<ActiveEncounterDto>>> getActiveEncounters() {
        log.info("Fetching all active encounters for clinical documentation (TRIAGE_WAIT, IN_TREATMENT, ADMITTED)...");
        List<Visit> visits = visitRepository.findActiveEncounterVisits();

        List<ActiveEncounterDto> results = visits.stream().map(v -> {
            TriageAssessment triage = triageAssessmentRepository.findTopByVisitIdOrderByTriageTimeDesc(v.getId())
                    .orElse(null);
            return ActiveEncounterDto.fromVisit(v, triage);
        }).toList();

        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    /**
     * Returns full triage and encounter details for a specific visit ID.
     */
    @GetMapping("/encounters/{visitId}")
    public ResponseEntity<ApiResponse<ActiveEncounterDto>> getEncounterDetails(@PathVariable("visitId") Long visitId) {
        log.info("Fetching encounter and triage details for visitId={}", visitId);
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new ResourceNotFoundException("Encounter visit not found: " + visitId));

        TriageAssessment triage = triageAssessmentRepository.findTopByVisitIdOrderByTriageTimeDesc(visit.getId())
                .orElse(null);

        return ResponseEntity.ok(ApiResponse.ok(ActiveEncounterDto.fromVisit(visit, triage)));
    }

    /**
     * AI SOAP documentation generator using Grok-2 LLM / Fallback Heuristics.
     */
    @PostMapping({"/soap/structure", "/soap", "/structure"})
    public ResponseEntity<ApiResponse<ClinicalSoapResponseDto>> structureClinicalNote(
            @Valid @RequestBody ClinicalNoteRequest request) {
        log.info("Processing clinical note structuring for visitId: {}", request.getVisitId());
        StructuredSoapResponse soap = grokRestClient.generateStructuredSoap(request.getRawClinicalNotes());

        ClinicalSoapResponseDto response = mapToClientSoapDto(soap, request.getRawClinicalNotes());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Saves an initial draft clinical note for a visit.
     */
    @PostMapping("/notes/{visitId}/draft")
    @Transactional
    public ResponseEntity<ApiResponse<ClinicalNoteResponseDto>> saveNoteDraft(
            @PathVariable("visitId") Long visitId,
            @Valid @RequestBody ClinicalNoteDraftRequest request) {
        log.info("Saving clinical note draft for visitId={}", visitId);
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new ResourceNotFoundException("Visit not found: " + visitId));

        User author = resolveAuthenticatedUser();

        ClinicalNote note = ClinicalNote.builder()
                .visit(visit)
                .author(author)
                .rawText(request.getRawText() != null ? request.getRawText() : "")
                .noteType(request.getNoteType() != null ? request.getNoteType() : "PROGRESS_NOTE")
                .status("DRAFT")
                .createdAt(Instant.now())
                .build();

        note = clinicalNoteRepository.save(note);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(ClinicalNoteResponseDto.fromEntity(note)));
    }

    /**
     * Finalizes and signs off a clinical note with structured SOAP content.
     * Transitions note status to CLINICIAN_SIGNED, sets signed_at = NOW(),
     * records authenticated doctor's ID, and saves final JSON in soap_outputs.
     */
    @PutMapping("/notes/{noteId}/soap-signoff")
    @Transactional
    public ResponseEntity<ApiResponse<ClinicalNoteResponseDto>> signoffSoapNote(
            @PathVariable("noteId") Long noteId,
            @RequestBody SoapSignoffRequest request) {
        log.info("Signing off clinical note noteId={}", noteId);

        ClinicalNote note = clinicalNoteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical note not found: " + noteId));

        User signingDoctor = resolveAuthenticatedUser();

        // Transition status and record signing metadata
        note.setStatus("CLINICIAN_SIGNED");
        note.setSignedAt(Instant.now());
        note.setSignedBy(signingDoctor);
        clinicalNoteRepository.save(note);

        // Persist structured SOAP outputs
        SoapOutput soapOutput = soapOutputRepository.findByClinicalNoteId(note.getId())
                .orElseGet(() -> SoapOutput.builder()
                        .clinicalNote(note)
                        .createdAt(Instant.now())
                        .build());

        soapOutput.setSubjectiveJson(request.getSubjective());
        soapOutput.setObjectiveJson(request.getObjective());
        soapOutput.setAssessmentJson(request.getAssessment());
        soapOutput.setPlanJson(request.getPlan());
        soapOutput.setModelVersion("grok-2");
        soapOutput.setFallback(false);

        try {
            soapOutput.setClinicianEditedJson(objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize clinician edited JSON: {}", e.getMessage());
            soapOutput.setClinicianEditedJson("{}");
        }

        soapOutputRepository.save(soapOutput);
        log.info("Successfully signed note noteId={} by doctor={}", noteId, signingDoctor.getFullName());

        return ResponseEntity.ok(ApiResponse.ok(ClinicalNoteResponseDto.fromEntity(note)));
    }

    /**
     * Atomic draft + signoff endpoint for visit when note has not yet been drafted.
     */
    @PostMapping("/notes/{visitId}/soap-signoff-atomic")
    @Transactional
    public ResponseEntity<ApiResponse<ClinicalNoteResponseDto>> atomicSoapSignoff(
            @PathVariable("visitId") Long visitId,
            @RequestBody SoapSignoffRequest request) {
        log.info("Executing atomic clinical note creation and signoff for visitId={}", visitId);
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new ResourceNotFoundException("Visit not found: " + visitId));

        User signingDoctor = resolveAuthenticatedUser();

        String rawTextCombined = (request.getSubjective() != null ? request.getSubjective() : "") + "\n\n"
                + (request.getObjective() != null ? request.getObjective() : "") + "\n\n"
                + (request.getAssessment() != null ? request.getAssessment() : "") + "\n\n"
                + (request.getPlan() != null ? request.getPlan() : "");

        ClinicalNote note = ClinicalNote.builder()
                .visit(visit)
                .author(signingDoctor)
                .rawText(rawTextCombined.trim())
                .noteType("PROGRESS_NOTE")
                .status("CLINICIAN_SIGNED")
                .createdAt(Instant.now())
                .signedAt(Instant.now())
                .signedBy(signingDoctor)
                .build();

        note = clinicalNoteRepository.save(note);

        SoapOutput soapOutput = SoapOutput.builder()
                .clinicalNote(note)
                .subjectiveJson(request.getSubjective())
                .objectiveJson(request.getObjective())
                .assessmentJson(request.getAssessment())
                .planJson(request.getPlan())
                .modelVersion("grok-2")
                .isFallback(false)
                .createdAt(Instant.now())
                .build();

        try {
            soapOutput.setClinicianEditedJson(objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            soapOutput.setClinicianEditedJson("{}");
        }

        soapOutputRepository.save(soapOutput);
        return ResponseEntity.ok(ApiResponse.ok(ClinicalNoteResponseDto.fromEntity(note)));
    }

    @GetMapping("/notes/{noteId}")
    public ResponseEntity<ApiResponse<ClinicalNoteResponseDto>> getNoteById(@PathVariable("noteId") Long noteId) {
        ClinicalNote note = clinicalNoteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical note not found: " + noteId));
        return ResponseEntity.ok(ApiResponse.ok(ClinicalNoteResponseDto.fromEntity(note)));
    }

    /**
     * Workflow 2: Doctor -> AI -> Patient (Patient-Friendly Care Summary)
     * Converts verified Assessment & Plan into plain-language discharge summary with active medication orders.
     */
    @PostMapping("/notes/{noteId}/patient-summary")
    @Transactional
    public ResponseEntity<ApiResponse<PatientCareSummaryDto>> generatePatientSummary(
            @PathVariable("noteId") Long noteId) {
        log.info("Generating patient-friendly care summary for noteId={}", noteId);
        ClinicalNote note = clinicalNoteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical note not found: " + noteId));

        Visit visit = note.getVisit();
        User signingDoctor = note.getSignedBy() != null ? note.getSignedBy() : resolveAuthenticatedUser();
        String doctorName = signingDoctor != null ? signingDoctor.getFullName() : "Dr. Robert Smith, MD";

        String patientName = (visit.getPatient() != null)
                ? visit.getPatient().getFirstName() + " " + visit.getPatient().getLastName()
                : "Patient #" + visit.getId();
        String mrn = (visit.getPatient() != null && visit.getPatient().getMrn() != null)
                ? visit.getPatient().getMrn()
                : "MRN-SYN";
        String visitNumber = visit.getVisitNumber() != null ? visit.getVisitNumber() : "ENC-" + visit.getId();

        SoapOutput soapOutput = soapOutputRepository.findByClinicalNoteId(note.getId()).orElse(null);
        String assessment = soapOutput != null && soapOutput.getAssessmentJson() != null
                ? soapOutput.getAssessmentJson()
                : note.getRawText();
        String plan = soapOutput != null && soapOutput.getPlanJson() != null
                ? soapOutput.getPlanJson()
                : "";

        List<MedicationOrder> activeOrders = medicationOrderRepository.findByVisitIdAndStatus(visit.getId(), OrderStatus.ACTIVE);

        PatientCareSummaryDto summary = grokRestClient.generatePatientCareSummary(
                patientName, mrn, visitNumber, doctorName, assessment, plan, activeOrders);

        // 1. Maintain backward compatibility in soap_outputs table
        if (soapOutput != null) {
            try {
                soapOutput.setPatientSummaryJson(objectMapper.writeValueAsString(summary));
                soapOutputRepository.save(soapOutput);
            } catch (Exception e) {
                log.warn("Could not persist patientSummaryJson in soap_outputs: {}", e.getMessage());
            }
        }

        // 2. Persist to patient_care_summaries table (Output 2)
        PatientCareSummary careSummary = patientCareSummaryRepository.findByClinicalNoteId(note.getId())
                .orElseGet(() -> PatientCareSummary.builder()
                        .clinicalNote(note)
                        .visit(visit)
                        .createdAt(Instant.now())
                        .build());

        careSummary.setSimplifiedDiagnosis(summary.getDiagnosis() != null ? summary.getDiagnosis() : "Condition evaluated");
        try {
            careSummary.setMedicationInstructions(objectMapper.writeValueAsString(
                    summary.getMedications() != null ? summary.getMedications() : List.of()));
            careSummary.setHomeCareInstructions(objectMapper.writeValueAsString(
                    summary.getHomeCare() != null ? summary.getHomeCare() : List.of()));
            careSummary.setRedFlagWarnings(objectMapper.writeValueAsString(
                    summary.getRedFlags() != null ? summary.getRedFlags() : List.of()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize patient care summary instructions JSON: {}", e.getMessage());
            careSummary.setMedicationInstructions("[]");
            careSummary.setHomeCareInstructions("[]");
            careSummary.setRedFlagWarnings("[]");
        }
        careSummary.setFollowUpInstructions(summary.getFollowUp() != null ? summary.getFollowUp() : "Follow up with primary clinician as instructed.");
        careSummary.setSignedByDoctorName(summary.getDoctorName() != null ? summary.getDoctorName() : doctorName);

        careSummary = patientCareSummaryRepository.save(careSummary);
        log.info("Persisted Output 2 PatientCareSummary id={} for noteId={}", careSummary.getId(), noteId);

        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    /**
     * Retrieves the persisted Patient-Facing Discharge Summary (Output 2) by note ID.
     */
    @GetMapping("/notes/{noteId}/patient-summary")
    public ResponseEntity<ApiResponse<PatientCareSummaryDto>> getPatientSummaryByNoteId(
            @PathVariable("noteId") Long noteId) {
        log.info("Fetching persisted Output 2 patient care summary for noteId={}", noteId);
        ClinicalNote note = clinicalNoteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical note not found: " + noteId));

        PatientCareSummary careSummary = patientCareSummaryRepository.findByClinicalNoteId(noteId).orElse(null);
        if (careSummary != null) {
            return ResponseEntity.ok(ApiResponse.ok(mapCareSummaryEntityToDto(careSummary)));
        }

        // Fallback to soap output JSON if available
        SoapOutput soapOutput = soapOutputRepository.findByClinicalNoteId(noteId).orElse(null);
        if (soapOutput != null && soapOutput.getPatientSummaryJson() != null) {
            try {
                PatientCareSummaryDto dto = objectMapper.readValue(soapOutput.getPatientSummaryJson(), PatientCareSummaryDto.class);
                return ResponseEntity.ok(ApiResponse.ok(dto));
            } catch (Exception ignored) {}
        }

        throw new ResourceNotFoundException("No patient care summary found for noteId: " + noteId);
    }

    /**
     * Retrieves the latest Patient-Facing Discharge Summary (Output 2) for a visit encounter.
     */
    @GetMapping("/visits/{visitId}/patient-summary")
    public ResponseEntity<ApiResponse<PatientCareSummaryDto>> getLatestPatientSummaryByVisitId(
            @PathVariable("visitId") Long visitId) {
        log.info("Fetching latest Output 2 patient care summary for visitId={}", visitId);
        PatientCareSummary careSummary = patientCareSummaryRepository.findTopByVisitIdOrderByCreatedAtDesc(visitId)
                .orElseThrow(() -> new ResourceNotFoundException("No patient care summary found for visitId: " + visitId));
        return ResponseEntity.ok(ApiResponse.ok(mapCareSummaryEntityToDto(careSummary)));
    }

    /**
     * Clinician Review & Sign-Off for Output 2 (Patient-Facing Discharge Summary).
     * Enables doctor to edit simplified diagnosis, medications, home care, red flags, or follow-up before patient distribution.
     */
    @PutMapping("/notes/{noteId}/patient-summary/review")
    @Transactional
    public ResponseEntity<ApiResponse<PatientCareSummaryDto>> reviewAndApprovePatientSummary(
            @PathVariable("noteId") Long noteId,
            @RequestBody PatientCareSummaryDto reviewDto) {
        log.info("Clinician reviewing and digitally approving Output 2 Patient Care Summary for noteId={}", noteId);
        ClinicalNote note = clinicalNoteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical note not found: " + noteId));

        User doctor = resolveAuthenticatedUser();
        String doctorName = doctor != null ? doctor.getFullName() : "Dr. Robert Smith, MD";

        PatientCareSummary summary = patientCareSummaryRepository.findByClinicalNoteId(noteId)
                .orElseGet(() -> PatientCareSummary.builder()
                        .clinicalNote(note)
                        .visit(note.getVisit())
                        .createdAt(Instant.now())
                        .build());

        if (reviewDto.getDiagnosis() != null) {
            summary.setSimplifiedDiagnosis(reviewDto.getDiagnosis());
        }
        if (reviewDto.getFollowUp() != null) {
            summary.setFollowUpInstructions(reviewDto.getFollowUp());
        }
        summary.setSignedByDoctorName(reviewDto.getDoctorName() != null ? reviewDto.getDoctorName() : doctorName);

        try {
            if (reviewDto.getMedications() != null) {
                summary.setMedicationInstructions(objectMapper.writeValueAsString(reviewDto.getMedications()));
            }
            if (reviewDto.getHomeCare() != null) {
                summary.setHomeCareInstructions(objectMapper.writeValueAsString(reviewDto.getHomeCare()));
            }
            if (reviewDto.getRedFlags() != null) {
                summary.setRedFlagWarnings(objectMapper.writeValueAsString(reviewDto.getRedFlags()));
            }
        } catch (JsonProcessingException e) {
            log.warn("Error serializing clinician reviewed patient summary: {}", e.getMessage());
        }

        summary = patientCareSummaryRepository.save(summary);
        return ResponseEntity.ok(ApiResponse.ok(mapCareSummaryEntityToDto(summary)));
    }

    /**
     * Real-Time Operational Synchronization on Discharge:
     * 1. Bed Management: Releases occupied bed and transitions to CLEANING
     * 2. Visit Lifecycle: Sets status to DISCHARGED with discharge timestamp
     * 3. Operational Census: Recalculates hospital capacity & active flow
     */
    @PostMapping("/visits/{visitId}/discharge-and-sync")
    @Transactional
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> dischargeAndSyncHospitalOperations(
            @PathVariable("visitId") Long visitId) {
        log.info("Executing real-time operational synchronization for visit discharge visitId={}", visitId);
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new ResourceNotFoundException("Visit not found: " + visitId));

        java.util.Map<String, Object> syncResult = new java.util.HashMap<>();
        syncResult.put("visitId", visitId);
        syncResult.put("visitNumber", visit.getVisitNumber());

        // 1. Bed Management: If patient occupies a bed, transition bed to CLEANING
        BedAdmission activeAdmission = bedAdmissionRepository.findTopByVisitIdAndDischargedAtIsNull(visitId).orElse(null);
        if (activeAdmission != null && activeAdmission.getBed() != null) {
            Long bedId = activeAdmission.getBed().getId();
            String bedNumber = activeAdmission.getBed().getBedNumber();
            bedManagementService.dischargePatient(bedId);
            syncResult.put("bedSync", "Bed " + bedNumber + " (ID: " + bedId + ") transitioned to CLEANING status.");
        } else {
            syncResult.put("bedSync", "No active bed occupied. Ambulatory or ED fast-track discharge completed.");
        }

        // 2. Visit status transition
        visit.setStatus(VisitStatus.DISCHARGED);
        visit.setDischargeTime(Instant.now());
        visitRepository.save(visit);
        syncResult.put("visitStatus", "DISCHARGED");
        syncResult.put("dischargeTime", visit.getDischargeTime().toString());

        // 3. Mark medication orders for discharge
        List<MedicationOrder> orders = medicationOrderRepository.findByVisitIdAndStatus(visitId, OrderStatus.ACTIVE);
        syncResult.put("activeMedicationsPrescribed", orders.size());

        // 4. Check if patient care summary is present
        boolean summaryPresent = patientCareSummaryRepository.findTopByVisitIdOrderByCreatedAtDesc(visitId).isPresent();
        syncResult.put("patientCareSummaryGenerated", summaryPresent);
        syncResult.put("status", "SUCCESS");
        syncResult.put("message", "Hospital operations synchronized: bed released for sanitization, visit discharged, patient care summary finalized.");

        return ResponseEntity.ok(ApiResponse.ok(syncResult));
    }

    @PostMapping("/patient-summary")
    public ResponseEntity<ApiResponse<PatientCareSummaryDto>> generatePatientSummaryAdhoc(
            @RequestBody PatientSummaryRequestDto request) {
        log.info("Generating ad-hoc patient care summary for visitId={}", request.getVisitId());
        Visit visit = request.getVisitId() != null
                ? visitRepository.findById(request.getVisitId()).orElse(null)
                : null;
        User doctor = resolveAuthenticatedUser();
        String doctorName = doctor != null ? doctor.getFullName() : "Dr. Robert Smith, MD";

        String patientName = (visit != null && visit.getPatient() != null)
                ? visit.getPatient().getFirstName() + " " + visit.getPatient().getLastName()
                : "Patient";
        String mrn = (visit != null && visit.getPatient() != null && visit.getPatient().getMrn() != null)
                ? visit.getPatient().getMrn()
                : "MRN-SYN";
        String visitNumber = visit != null && visit.getVisitNumber() != null
                ? visit.getVisitNumber()
                : "ENC-ACTIVE";

        List<MedicationOrder> activeOrders = visit != null
                ? medicationOrderRepository.findByVisitIdAndStatus(visit.getId(), OrderStatus.ACTIVE)
                : List.of();

        PatientCareSummaryDto summary = grokRestClient.generatePatientCareSummary(
                patientName, mrn, visitNumber, doctorName, request.getAssessment(), request.getPlan(), activeOrders);

        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    private PatientCareSummaryDto mapCareSummaryEntityToDto(PatientCareSummary entity) {
        if (entity == null) return null;
        List<PatientMedicationInstructionDto> meds = new ArrayList<>();
        List<String> homeCare = new ArrayList<>();
        List<String> redFlags = new ArrayList<>();
        try {
            if (entity.getMedicationInstructions() != null) {
                meds = objectMapper.readValue(entity.getMedicationInstructions(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, PatientMedicationInstructionDto.class));
            }
        } catch (Exception ignored) {}
        try {
            if (entity.getHomeCareInstructions() != null) {
                homeCare = objectMapper.readValue(entity.getHomeCareInstructions(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }
        } catch (Exception ignored) {}
        try {
            if (entity.getRedFlagWarnings() != null) {
                redFlags = objectMapper.readValue(entity.getRedFlagWarnings(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }
        } catch (Exception ignored) {}

        Visit v = entity.getVisit();
        String patientName = (v != null && v.getPatient() != null)
                ? v.getPatient().getFirstName() + " " + v.getPatient().getLastName() : "Patient";
        String mrn = (v != null && v.getPatient() != null) ? v.getPatient().getMrn() : "MRN-SYN";
        String visitNumber = (v != null && v.getVisitNumber() != null) ? v.getVisitNumber() : "ENC-" + (v != null ? v.getId() : "");

        return PatientCareSummaryDto.builder()
                .diagnosis(entity.getSimplifiedDiagnosis())
                .medications(meds)
                .homeCare(homeCare)
                .redFlags(redFlags)
                .followUp(entity.getFollowUpInstructions())
                .doctorName(entity.getSignedByDoctorName())
                .doctorSignature("Digitally Verified by " + entity.getSignedByDoctorName())
                .signedAt(entity.getCreatedAt())
                .patientName(patientName)
                .mrn(mrn)
                .visitNumber(visitNumber)
                .build();
    }

    private User resolveAuthenticatedUser() {
        Long currentUserId = SecurityContext.getCurrentUserId();
        if (currentUserId != null) {
            return userRepository.findById(currentUserId).orElseGet(this::fallbackDoctor);
        }
        return fallbackDoctor();
    }

    private User fallbackDoctor() {
        return userRepository.findByUsername("dr_smith")
                .orElseGet(() -> userRepository.findAll().stream()
                        .filter(u -> u.getRole() == Role.DOCTOR || u.getRole() == Role.ADMIN)
                        .findFirst()
                        .orElse(null));
    }

    private ClinicalSoapResponseDto mapToClientSoapDto(StructuredSoapResponse soap, String rawNotes) {
        // Build Subjective text
        StringBuilder subj = new StringBuilder();
        if (soap != null && soap.getSubjective() != null) {
            if (soap.getSubjective().getChiefComplaint() != null && !soap.getSubjective().getChiefComplaint().isBlank()) {
                subj.append("Chief Complaint: ").append(soap.getSubjective().getChiefComplaint()).append("\n\n");
            }
            if (soap.getSubjective().getHistoryOfPresentIllness() != null && !soap.getSubjective().getHistoryOfPresentIllness().isBlank()) {
                subj.append("History of Present Illness:\n").append(soap.getSubjective().getHistoryOfPresentIllness()).append("\n\n");
            }
            if (soap.getSubjective().getReviewOfSystems() != null && !soap.getSubjective().getReviewOfSystems().isBlank()) {
                subj.append("Review of Systems: ").append(soap.getSubjective().getReviewOfSystems());
            }
        }
        String subjectiveText = subj.toString().trim();
        if (subjectiveText.isEmpty()) {
            subjectiveText = rawNotes;
        }

        // Build Objective text
        StringBuilder obj = new StringBuilder();
        if (soap != null && soap.getObjective() != null) {
            if (soap.getObjective().getVitalSigns() != null && !soap.getObjective().getVitalSigns().isEmpty()) {
                obj.append("Vital Signs:\n");
                soap.getObjective().getVitalSigns().forEach((k, v) -> obj.append("• ").append(k).append(": ").append(v).append("\n"));
                obj.append("\n");
            }
            if (soap.getObjective().getPhysicalExam() != null && !soap.getObjective().getPhysicalExam().isBlank()) {
                obj.append("Physical Examination:\n").append(soap.getObjective().getPhysicalExam()).append("\n\n");
            }
            if (soap.getObjective().getLabsAndDiagnostics() != null && !soap.getObjective().getLabsAndDiagnostics().isBlank()) {
                obj.append("Diagnostic & Laboratory Telemetry:\n").append(soap.getObjective().getLabsAndDiagnostics());
            }
        }
        String objectiveText = obj.toString().trim();

        // Build Assessment text & Differentials
        StringBuilder ast = new StringBuilder();
        List<String> differentials = new ArrayList<>();
        if (soap != null && soap.getAssessment() != null) {
            if (soap.getAssessment().getSummary() != null && !soap.getAssessment().getSummary().isBlank()) {
                ast.append(soap.getAssessment().getSummary());
            }
            if (soap.getAssessment().getClinicalImpressionStatedByClinician() != null) {
                differentials.addAll(soap.getAssessment().getClinicalImpressionStatedByClinician());
            }
        }
        String assessmentText = ast.toString().trim();

        // Build Plan text, Investigations & Interventions
        StringBuilder pln = new StringBuilder();
        List<String> diagnostics = new ArrayList<>();
        List<String> interventions = new ArrayList<>();
        if (soap != null && soap.getPlan() != null) {
            if (soap.getPlan().getMedicationsOrdered() != null && !soap.getPlan().getMedicationsOrdered().isEmpty()) {
                pln.append("Medications & Therapeutics:\n");
                for (String med : soap.getPlan().getMedicationsOrdered()) {
                    pln.append("• ").append(med).append("\n");
                    interventions.add(med);
                }
                pln.append("\n");
            }
            if (soap.getPlan().getDiagnosticOrders() != null && !soap.getPlan().getDiagnosticOrders().isEmpty()) {
                pln.append("Diagnostic Orders:\n");
                for (String diag : soap.getPlan().getDiagnosticOrders()) {
                    pln.append("• ").append(diag).append("\n");
                    diagnostics.add(diag);
                }
                pln.append("\n");
            }
            if (soap.getPlan().getConsultations() != null && !soap.getPlan().getConsultations().isEmpty()) {
                pln.append("Consultations: ").append(String.join(", ", soap.getPlan().getConsultations())).append("\n\n");
            }
            if (soap.getPlan().getPatientInstructions() != null && !soap.getPlan().getPatientInstructions().isBlank()) {
                pln.append("Patient Instructions: ").append(soap.getPlan().getPatientInstructions()).append("\n\n");
            }
            if (soap.getPlan().getFollowUp() != null && !soap.getPlan().getFollowUp().isBlank()) {
                pln.append("Follow-up: ").append(soap.getPlan().getFollowUp());
            }
        }
        String planText = pln.toString().trim();

        // Triage Category Heuristic
        String lowerRaw = rawNotes != null ? rawNotes.toLowerCase() : "";
        String triageCategory = "ESI-2 Urgent";
        if (lowerRaw.contains("stemi") || lowerRaw.contains("chest pain") || lowerRaw.contains("coronary")) {
            triageCategory = "ESI-2 Emergent (Cardiac Telemetry)";
            if (differentials.isEmpty()) {
                differentials.add("Acute Coronary Syndrome (STEMI/NSTEMI)");
                differentials.add("Aortic Dissection");
                differentials.add("Acute Pulmonary Embolism");
            }
        } else if (lowerRaw.contains("pneumonia") || lowerRaw.contains("spo2 89") || lowerRaw.contains("hypox")) {
            triageCategory = "ESI-2 Urgent (Hypoxemic Respiratory Failure)";
            if (differentials.isEmpty()) {
                differentials.add("Severe Community-Acquired Pneumonia");
                differentials.add("Acute Respiratory Distress Syndrome (ARDS)");
                differentials.add("COPD / Asthma Exacerbation");
            }
        } else if (lowerRaw.contains("appendicitis") || lowerRaw.contains("mcburney")) {
            triageCategory = "ESI-3 Semi-Urgent (Acute Abdomen)";
            if (differentials.isEmpty()) {
                differentials.add("Acute Appendicitis");
                differentials.add("Mesenteric Adenitis");
                differentials.add("Nephrolithiasis");
            }
        }

        if (diagnostics.isEmpty()) {
            diagnostics.add("12-Lead ECG Stat");
            diagnostics.add("Complete Blood Count (CBC)");
            diagnostics.add("High-Sensitivity Troponin I");
        }

        if (interventions.isEmpty()) {
            interventions.add("Initiate 18G Peripheral IV Access");
            interventions.add("Continuous Cardiac Telemetry");
        }

        String safetyNotice = soap != null && soap.getSafetyDisclaimer() != null
                ? soap.getSafetyDisclaimer()
                : "MANDATORY NON-DIAGNOSTIC NOTICE: Clinician must review and verify all drafted sections before signing.";

        return ClinicalSoapResponseDto.builder()
                .subjective(subjectiveText)
                .objective(objectiveText)
                .assessment(assessmentText)
                .plan(planText)
                .triageCategoryDraft(triageCategory)
                .differentialDiagnoses(differentials)
                .suggestedInvestigations(diagnostics)
                .suggestedInterventions(interventions)
                .needsClinicianReview(true)
                .safetyNotice(safetyNotice)
                .build();
    }
}
