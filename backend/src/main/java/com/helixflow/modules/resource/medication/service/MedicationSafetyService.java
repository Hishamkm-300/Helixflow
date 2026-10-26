package com.helixflow.modules.resource.medication.service;

import com.helixflow.common.exception.BusinessRuleException;
import com.helixflow.common.exception.DosageLimitExceededException;
import com.helixflow.common.exception.ResourceNotFoundException;
import com.helixflow.modules.auth.entity.User;
import com.helixflow.modules.auth.repository.UserRepository;
import com.helixflow.modules.patient.entity.Visit;
import com.helixflow.modules.patient.repository.VisitRepository;
import com.helixflow.modules.resource.medication.dto.*;
import com.helixflow.modules.resource.medication.entity.*;
import com.helixflow.modules.resource.medication.repository.MedicationInteractionRepository;
import com.helixflow.modules.resource.medication.repository.MedicationOrderRepository;
import com.helixflow.modules.resource.medication.repository.MedicationRepository;
import com.helixflow.security.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationSafetyService implements MedicationService {

    private final MedicationRepository medicationRepository;
    private final MedicationInteractionRepository interactionRepository;
    private final MedicationOrderRepository orderRepository;
    private final VisitRepository visitRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    @Override
    public List<MedicationDto> searchMedications(String query) {
        if (query == null || query.isBlank()) {
            return medicationRepository.findAll().stream()
                    .map(MedicationDto::fromEntity)
                    .collect(Collectors.toList());
        }
        return medicationRepository.findByGenericNameContainingIgnoreCaseOrBrandNameContainingIgnoreCase(query, query)
                .stream()
                .map(MedicationDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public List<MedicationOrderDto> getActiveOrdersForVisit(Long visitId) {
        return orderRepository.findByVisitIdAndStatus(visitId, OrderStatus.ACTIVE).stream()
                .map(MedicationOrderDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public List<MedicationOrderDto> getOrdersForVisit(Long visitId) {
        return orderRepository.findByVisitIdOrderByCreatedAtDesc(visitId).stream()
                .map(MedicationOrderDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Deterministic Pair-Wise Interaction Safety Check
     */
    @Transactional(readOnly = true)
    @Override
    public SafetyCheckResultDto evaluateSafety(MedicationSafetyCheckRequest request) {
        Set<Long> allMedIds = new HashSet<>(request.getProposedMedicationIds());

        // Include currently active medications for this visit
        if (request.getVisitId() != null) {
            List<MedicationOrder> activeOrders = orderRepository.findByVisitIdAndStatus(request.getVisitId(), OrderStatus.ACTIVE);
            for (MedicationOrder order : activeOrders) {
                allMedIds.add(order.getMedication().getId());
            }
        }

        List<SafetyAlertDto> alerts = new ArrayList<>();
        List<Long> medList = new ArrayList<>(allMedIds);

        // Scan all pairwise combinations (O(N^2) over formulary subset N <= 10)
        for (int i = 0; i < medList.size(); i++) {
            for (int j = i + 1; j < medList.size(); j++) {
                Long med1 = medList.get(i);
                Long med2 = medList.get(j);
                Optional<MedicationInteractionRule> ruleOpt = interactionRepository.findInteractionBetween(med1, med2);
                ruleOpt.ifPresent(rule -> alerts.add(SafetyAlertDto.fromEntity(rule)));
            }
        }

        boolean hasSevere = alerts.stream().anyMatch(a -> a.getSeverity() == InteractionSeverity.SEVERE_CONTRAINDICATED);
        boolean hasModerate = alerts.stream().anyMatch(a -> a.getSeverity() == InteractionSeverity.MODERATE);

        String summary;
        if (hasSevere) {
            summary = "CRITICAL: Severe contraindicated drug-drug interaction detected. Prescription is hard-blocked without explicit clinical override.";
        } else if (hasModerate) {
            summary = "WARNING: Moderate drug-drug interaction detected. Clinical acknowledgment recommended.";
        } else if (!alerts.isEmpty()) {
            summary = "INFO: Mild drug interaction noted. Review monitoring guidelines.";
        } else {
            summary = "No adverse drug-drug interactions detected among evaluated medications.";
        }

        return SafetyCheckResultDto.builder()
                .safe(alerts.isEmpty())
                .requiresOverride(hasSevere || hasModerate)
                .hardBlocked(hasSevere)
                .alerts(alerts)
                .summaryMessage(summary)
                .build();
    }

    /**
     * Prescribes a medication order, enforcing safety rules and single-drug dosage limits.
     */
    @Transactional
    @Override
    public MedicationOrderDto prescribeMedication(MedicationPrescribeRequest request) {
        Visit visit = visitRepository.findById(request.getVisitId())
                .orElseThrow(() -> new ResourceNotFoundException("Visit not found: " + request.getVisitId()));

        Medication med = medicationRepository.findById(request.getMedicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Medication not found: " + request.getMedicationId()));

        // Validate Single-Drug Dosage Limits & Range Validation
        Double numericDose = request.getNumericDose();
        if (numericDose == null && request.getDose() != null) {
            numericDose = extractNumericDose(request.getDose());
        }

        if (numericDose != null && med.getMaxSingleDoseMg() != null) {
            if (numericDose > med.getMaxSingleDoseMg()) {
                String maxDoseStr = (med.getMaxSingleDoseMg() % 1 == 0)
                        ? String.valueOf(med.getMaxSingleDoseMg().longValue())
                        : String.valueOf(med.getMaxSingleDoseMg());
                String unitStr = (med.getUnit() != null && !med.getUnit().isBlank()) ? med.getUnit() : "mg";
                throw new DosageLimitExceededException("Safe threshold exceeded: maximum allowable dose is " + maxDoseStr + " " + unitStr);
            }
        }

        String finalDose = request.getDose();
        if (finalDose == null || finalDose.isBlank()) {
            if (numericDose != null) {
                String numStr = (numericDose % 1 == 0) ? String.valueOf(numericDose.longValue()) : String.valueOf(numericDose);
                String unitStr = (med.getUnit() != null && !med.getUnit().isBlank()) ? med.getUnit() : "mg";
                finalDose = numStr + unitStr;
            } else {
                finalDose = med.getStrength();
            }
        }

        Long doctorId = SecurityContext.getCurrentUserId();
        User doctor = userRepository.findById(doctorId != null ? doctorId : 1L)
                .orElseThrow(() -> new ResourceNotFoundException("Prescribing doctor not found"));

        // Evaluate safety against currently active medications
        SafetyCheckResultDto safetyResult = evaluateSafety(MedicationSafetyCheckRequest.builder()
                .visitId(visit.getId())
                .proposedMedicationIds(List.of(med.getId()))
                .build());

        if (safetyResult.isHardBlocked()) {
            if (!request.isInteractionOverridden() || request.getOverrideReason() == null || request.getOverrideReason().isBlank()) {
                throw new BusinessRuleException("SEVERE_INTERACTION_BLOCKED",
                        "Prescription blocked: Severe contraindicated interaction detected. "
                        + "A valid clinical override reason is mandatory to proceed.");
            }
            log.warn("Clinician {} overrode severe contraindicated interaction for Visit {}: {}",
                    doctor.getUsername(), visit.getId(), request.getOverrideReason());
        }

        MedicationOrder order = MedicationOrder.builder()
                .visit(visit)
                .medication(med)
                .prescribedBy(doctor)
                .dose(finalDose)
                .route(request.getRoute() != null ? request.getRoute() : "ORAL")
                .frequency(request.getFrequency() != null ? request.getFrequency() : "ONCE_DAILY")
                .status(OrderStatus.ACTIVE)
                .interactionOverridden(request.isInteractionOverridden())
                .overrideReason(request.getOverrideReason())
                .createdAt(Instant.now())
                .build();

        return MedicationOrderDto.fromEntity(orderRepository.save(order));
    }

    @Transactional
    @Override
    public MedicationOrderDto prescribeMedication(PrescriptionRequestDto request) {
        return prescribeMedication(request.toPrescribeRequest());
    }

    @Transactional
    @Override
    public MedicationOrderDto discontinueMedicationOrder(Long orderId, String reason) {
        MedicationOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Medication order not found: " + orderId));

        order.setStatus(OrderStatus.DISCONTINUED);
        order.setDiscontinuedAt(Instant.now());
        order.setDiscontinueReason(reason != null && !reason.isBlank() ? reason : "Discontinued by clinician");

        Long doctorId = SecurityContext.getCurrentUserId();
        User doctor = userRepository.findById(doctorId != null ? doctorId : 1L).orElse(null);
        order.setDiscontinuedBy(doctor);

        MedicationOrder saved = orderRepository.save(order);
        log.info("Medication order ID {} ({}) for Visit {} marked DISCONTINUED by {}. Reason: {}",
                saved.getId(), saved.getMedication().getGenericName(), saved.getVisit().getId(),
                doctor != null ? doctor.getUsername() : "SYSTEM", saved.getDiscontinueReason());

        return MedicationOrderDto.fromEntity(saved);
    }

    private Double extractNumericDose(String doseStr) {
        if (doseStr == null || doseStr.isBlank()) {
            return null;
        }
        try {
            Matcher matcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(doseStr);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }
        } catch (Exception ignored) {}
        return null;
    }
}

