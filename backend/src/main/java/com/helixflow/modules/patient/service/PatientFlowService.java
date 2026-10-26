package com.helixflow.modules.patient.service;

import com.helixflow.common.exception.ResourceNotFoundException;
import com.helixflow.modules.auth.entity.User;
import com.helixflow.modules.auth.repository.UserRepository;
import com.helixflow.modules.patient.dto.*;
import com.helixflow.modules.patient.entity.*;
import com.helixflow.modules.patient.repository.PatientRepository;
import com.helixflow.modules.patient.repository.TriageAssessmentRepository;
import com.helixflow.modules.patient.repository.VisitRepository;
import com.helixflow.security.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatientFlowService {

    private final PatientRepository patientRepository;
    private final VisitRepository visitRepository;
    private final TriageAssessmentRepository triageRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public PatientResponseDto registerPatient(PatientRegistrationDto dto) {
        String mrn = "SYN-" + (100000 + secureRandom.nextInt(900000));
        while (patientRepository.existsByMrn(mrn)) {
            mrn = "SYN-" + (100000 + secureRandom.nextInt(900000));
        }

        Patient patient = Patient.builder()
                .mrn(mrn)
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .dateOfBirth(dto.getDateOfBirth())
                .gender(dto.getGender())
                .phone(dto.getPhone())
                .emergencyContact(dto.getEmergencyContact())
                .createdAt(Instant.now())
                .build();

        return PatientResponseDto.fromEntity(patientRepository.save(patient));
    }

    @Transactional(readOnly = true)
    public List<PatientResponseDto> searchPatients(String query) {
        if (query == null || query.isBlank()) {
            return patientRepository.findAll().stream()
                    .limit(50)
                    .map(PatientResponseDto::fromEntity)
                    .collect(Collectors.toList());
        }
        return patientRepository.findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCase(query, query)
                .stream()
                .map(PatientResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PatientResponseDto getPatientById(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found with ID: " + id));
        return PatientResponseDto.fromEntity(patient);
    }

    @Transactional
    public VisitResponseDto createVisit(VisitCreateDto dto) {
        Patient patient = patientRepository.findById(dto.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found with ID: " + dto.getPatientId()));

        String visitNumber = "ENC-" + (100000 + secureRandom.nextInt(900000));

        Visit visit = Visit.builder()
                .patient(patient)
                .visitNumber(visitNumber)
                .visitType(dto.getVisitType())
                .status(VisitStatus.TRIAGE_WAIT)
                .arrivalTime(Instant.now())
                .chiefComplaint(dto.getChiefComplaint())
                .build();

        Visit savedVisit = visitRepository.save(visit);

        // Auto-seed initial baseline intake triage assessment so newly registered patient appears in the live queue immediately
        Long currentUserId = SecurityContext.getCurrentUserId();
        User intakeStaff = userRepository.findById(currentUserId != null ? currentUserId : 1L).orElse(null);

        TriageAssessment initialTriage = TriageAssessment.builder()
                .visit(savedVisit)
                .triagedBy(intakeStaff)
                .esiLevel(3) // Default to Urgent until clinical triage assessment is completed
                .heartRate(80)
                .bloodPressureSys(120)
                .bloodPressureDia(80)
                .respiratoryRate(16)
                .temperatureCelsius(37.0)
                .o2Saturation(98)
                .consciousnessLevel("ALERT")
                .triageNotes("Initial patient demographic intake completed. Awaiting full clinical triage evaluation.")
                .painScore(5)
                .symptomDuration("Acute presentation")
                .triageTime(Instant.now())
                .build();
        triageRepository.save(initialTriage);

        return VisitResponseDto.fromEntity(savedVisit);
    }

    @Transactional
    public TriageQueueItemDto recordTriage(TriageAssessmentDto dto) {
        Visit visit = visitRepository.findById(dto.getVisitId())
                .orElseThrow(() -> new ResourceNotFoundException("Visit not found with ID: " + dto.getVisitId()));

        Long currentUserId = SecurityContext.getCurrentUserId();
        User nurse = userRepository.findById(currentUserId != null ? currentUserId : 1L)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found in database"));

        // If an initial triage assessment exists for this visit, update it, otherwise create new
        TriageAssessment triage = triageRepository.findTopByVisitIdOrderByTriageTimeDesc(visit.getId())
                .orElse(null);

        if (triage == null) {
            triage = TriageAssessment.builder().visit(visit).build();
        }

        triage.setTriagedBy(nurse);
        triage.setEsiLevel(dto.getEsiLevel());
        triage.setHeartRate(dto.getHeartRate());
        triage.setBloodPressureSys(dto.getBloodPressureSys());
        triage.setBloodPressureDia(dto.getBloodPressureDia());
        triage.setRespiratoryRate(dto.getRespiratoryRate());
        triage.setTemperatureCelsius(dto.getTemperatureCelsius());
        triage.setO2Saturation(dto.getO2Saturation());
        triage.setConsciousnessLevel(dto.getConsciousnessLevel() != null ? dto.getConsciousnessLevel() : "ALERT");
        triage.setTriageNotes(dto.getTriageNotes());
        triage.setPainScore(dto.getPainScore());
        triage.setSymptomDuration(dto.getSymptomDuration());
        triage.setTriageTime(Instant.now());

        triage = triageRepository.save(triage);

        // Update visit status based on urgency
        if (dto.getEsiLevel() == 1) {
            visit.setStatus(VisitStatus.IN_TREATMENT);
        } else {
            visit.setStatus(VisitStatus.TRIAGE_WAIT);
        }
        visitRepository.save(visit);

        return TriageQueueItemDto.fromEntity(triage);
    }

    @Transactional(readOnly = true)
    public List<TriageQueueItemDto> getActiveTriageQueue() {
        return triageRepository.findActiveTriageQueue().stream()
                .map(TriageQueueItemDto::fromEntity)
                .collect(Collectors.toList());
    }
}
