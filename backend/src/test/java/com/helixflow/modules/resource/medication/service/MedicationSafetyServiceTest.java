package com.helixflow.modules.resource.medication.service;

import com.helixflow.common.exception.BusinessRuleException;
import com.helixflow.common.exception.ResourceNotFoundException;
import com.helixflow.modules.auth.entity.User;
import com.helixflow.modules.auth.repository.UserRepository;
import com.helixflow.modules.patient.entity.Patient;
import com.helixflow.modules.patient.entity.Visit;
import com.helixflow.modules.patient.repository.VisitRepository;
import com.helixflow.modules.resource.medication.dto.*;
import com.helixflow.modules.resource.medication.entity.*;
import com.helixflow.modules.resource.medication.repository.MedicationInteractionRepository;
import com.helixflow.modules.resource.medication.repository.MedicationOrderRepository;
import com.helixflow.modules.resource.medication.repository.MedicationRepository;
import com.helixflow.security.SecurityContext;
import com.helixflow.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicationSafetyServiceTest {

    @Mock
    private MedicationRepository medicationRepository;

    @Mock
    private MedicationInteractionRepository interactionRepository;

    @Mock
    private MedicationOrderRepository orderRepository;

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MedicationSafetyService medicationSafetyService;

    private Medication warfarin;
    private Medication aspirin;
    private Medication lisinopril;
    private MedicationInteractionRule severeRule;
    private Visit visit;
    private User doctor;

    @BeforeEach
    void setUp() {
        warfarin = Medication.builder()
                .id(1L)
                .code("RX-WARFARIN")
                .genericName("Warfarin Sodium")
                .brandName("Coumadin")
                .dosageForm("TABLET")
                .strength("5mg")
                .maxSingleDoseMg(20.0)
                .unit("mg")
                .build();

        aspirin = Medication.builder()
                .id(2L)
                .code("RX-ASPIRIN")
                .genericName("Aspirin")
                .brandName("Bayer")
                .dosageForm("TABLET")
                .strength("81mg")
                .maxSingleDoseMg(1000.0)
                .unit("mg")
                .build();

        lisinopril = Medication.builder()
                .id(3L)
                .code("RX-LISINOPRIL")
                .genericName("Lisinopril")
                .brandName("Zestril")
                .dosageForm("TABLET")
                .strength("10mg")
                .maxSingleDoseMg(80.0)
                .unit("mg")
                .build();

        severeRule = MedicationInteractionRule.builder()
                .id(10L)
                .primaryMed(warfarin)
                .interactingMed(aspirin)
                .severity(InteractionSeverity.SEVERE_CONTRAINDICATED)
                .clinicalEffect("Synergistic bleeding hazard exponentially increases intracranial hemorrhage risk.")
                .managementAdvice("Hard contraindication. Discontinue one agent or document override with serial coag monitoring.")
                .build();

        visit = Visit.builder()
                .id(50L)
                .visitNumber("ENC-50")
                .patient(Patient.builder().firstName("Alice").lastName("Smith").build())
                .build();

        doctor = User.builder()
                .id(1L)
                .username("dr_smith")
                .fullName("Dr. Robert Smith, MD")
                .build();

        SecurityContext.set(UserPrincipal.builder()
                .userId(1L)
                .username("dr_smith")
                .fullName("Dr. Robert Smith, MD")
                .role(com.helixflow.security.Role.DOCTOR)
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    @Test
    @DisplayName("Evaluate safety returns safe when no interaction rules match")
    void testEvaluateSafety_Safe() {
        MedicationSafetyCheckRequest request = MedicationSafetyCheckRequest.builder()
                .proposedMedicationIds(List.of(1L, 3L)) // Warfarin + Lisinopril
                .build();

        when(interactionRepository.findInteractionBetween(anyLong(), anyLong())).thenReturn(Optional.empty());

        SafetyCheckResultDto result = medicationSafetyService.evaluateSafety(request);

        assertNotNull(result);
        assertTrue(result.isSafe());
        assertFalse(result.isHardBlocked());
        assertFalse(result.isRequiresOverride());
        assertTrue(result.getAlerts().isEmpty());
    }

    @Test
    @DisplayName("Evaluate safety flags SEVERE_CONTRAINDICATED interaction with hardBlocked=true")
    void testEvaluateSafety_SevereInteraction() {
        MedicationSafetyCheckRequest request = MedicationSafetyCheckRequest.builder()
                .proposedMedicationIds(List.of(1L, 2L)) // Warfarin + Aspirin
                .build();

        when(interactionRepository.findInteractionBetween(1L, 2L)).thenReturn(Optional.of(severeRule));

        SafetyCheckResultDto result = medicationSafetyService.evaluateSafety(request);

        assertNotNull(result);
        assertFalse(result.isSafe());
        assertTrue(result.isHardBlocked());
        assertTrue(result.isRequiresOverride());
        assertEquals(1, result.getAlerts().size());
        assertEquals(InteractionSeverity.SEVERE_CONTRAINDICATED, result.getAlerts().get(0).getSeverity());
        assertTrue(result.getSummaryMessage().contains("CRITICAL: Severe contraindicated"));
    }

    @Test
    @DisplayName("Prescribing blocked when severe interaction exists without clinician override")
    void testPrescribeMedication_BlockedWithoutOverride() {
        // Patient currently taking Warfarin
        MedicationOrder existingOrder = MedicationOrder.builder()
                .id(101L)
                .visit(visit)
                .medication(warfarin)
                .status(OrderStatus.ACTIVE)
                .build();

        when(visitRepository.findById(50L)).thenReturn(Optional.of(visit));
        when(medicationRepository.findById(2L)).thenReturn(Optional.of(aspirin));
        when(userRepository.findById(1L)).thenReturn(Optional.of(doctor));
        when(orderRepository.findByVisitIdAndStatus(50L, OrderStatus.ACTIVE)).thenReturn(List.of(existingOrder));
        when(interactionRepository.findInteractionBetween(anyLong(), anyLong())).thenReturn(Optional.of(severeRule));

        // Attempting to prescribe Aspirin without override
        MedicationPrescribeRequest request = MedicationPrescribeRequest.builder()
                .visitId(50L)
                .medicationId(2L)
                .dose("81mg")
                .route("ORAL")
                .frequency("DAILY")
                .interactionOverridden(false)
                .overrideReason(null)
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> medicationSafetyService.prescribeMedication(request));

        assertEquals("SEVERE_INTERACTION_BLOCKED", ex.getCode());
        assertTrue(ex.getMessage().contains("Severe contraindicated interaction detected"));
        verify(orderRepository, never()).save(any(MedicationOrder.class));
    }

    @Test
    @DisplayName("Prescribing permitted when severe interaction has explicit clinical override reason")
    void testPrescribeMedication_PermittedWithOverride() {
        MedicationOrder existingOrder = MedicationOrder.builder()
                .id(101L)
                .visit(visit)
                .medication(warfarin)
                .status(OrderStatus.ACTIVE)
                .build();

        when(visitRepository.findById(50L)).thenReturn(Optional.of(visit));
        when(medicationRepository.findById(2L)).thenReturn(Optional.of(aspirin));
        when(userRepository.findById(1L)).thenReturn(Optional.of(doctor));
        when(orderRepository.findByVisitIdAndStatus(50L, OrderStatus.ACTIVE)).thenReturn(List.of(existingOrder));
        when(interactionRepository.findInteractionBetween(anyLong(), anyLong())).thenReturn(Optional.of(severeRule));

        when(orderRepository.save(any(MedicationOrder.class))).thenAnswer(invocation -> {
            MedicationOrder o = invocation.getArgument(0);
            o.setId(205L);
            return o;
        });

        // Doctor provides explicit clinical justification
        MedicationPrescribeRequest request = MedicationPrescribeRequest.builder()
                .visitId(50L)
                .medicationId(2L)
                .dose("81mg")
                .route("ORAL")
                .frequency("DAILY")
                .interactionOverridden(true)
                .overrideReason("Urgent cardiac stent protection indicated. Daily INR and telemetry telemetry monitored.")
                .build();

        MedicationOrderDto result = medicationSafetyService.prescribeMedication(request);

        assertNotNull(result);
        assertEquals(205L, result.getId());
        assertTrue(result.isInteractionOverridden());
        assertEquals("Urgent cardiac stent protection indicated. Daily INR and telemetry telemetry monitored.", result.getOverrideReason());
        verify(orderRepository, times(1)).save(any(MedicationOrder.class));
    }

    @Test
    @DisplayName("Search medications by generic or brand name returns matches")
    void testSearchMedications() {
        when(medicationRepository.findByGenericNameContainingIgnoreCaseOrBrandNameContainingIgnoreCase("warf", "warf"))
                .thenReturn(List.of(warfarin));

        List<MedicationDto> results = medicationSafetyService.searchMedications("warf");
        assertEquals(1, results.size());
        assertEquals("Warfarin Sodium", results.get(0).getGenericName());
    }

    @Test
    @DisplayName("Prescribing throws DosageLimitExceededException when dose exceeds drug maxSingleDoseMg")
    void testPrescribeMedication_ExceedsDosageLimit() {
        when(visitRepository.findById(50L)).thenReturn(Optional.of(visit));
        when(medicationRepository.findById(2L)).thenReturn(Optional.of(aspirin));

        // Aspirin has maxSingleDoseMg = 1000mg. Attempting absurd dose 10000000000
        MedicationPrescribeRequest request = MedicationPrescribeRequest.builder()
                .visitId(50L)
                .medicationId(2L)
                .dose("10000000000")
                .route("ORAL")
                .frequency("DAILY")
                .build();

        com.helixflow.common.exception.DosageLimitExceededException ex = assertThrows(
                com.helixflow.common.exception.DosageLimitExceededException.class,
                () -> medicationSafetyService.prescribeMedication(request)
        );

        assertTrue(ex.getMessage().contains("Safe threshold exceeded: maximum allowable dose is 1000 mg"));
        verify(orderRepository, never()).save(any(MedicationOrder.class));
    }

    @Test
    @DisplayName("Discontinue medication order transitions status to DISCONTINUED with audit reason")
    void testDiscontinueMedicationOrder() {
        MedicationOrder activeOrder = MedicationOrder.builder()
                .id(301L)
                .visit(visit)
                .medication(aspirin)
                .prescribedBy(doctor)
                .dose("81mg")
                .status(OrderStatus.ACTIVE)
                .build();

        when(orderRepository.findById(301L)).thenReturn(Optional.of(activeOrder));
        when(userRepository.findById(1L)).thenReturn(Optional.of(doctor));
        when(orderRepository.save(any(MedicationOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MedicationOrderDto result = medicationSafetyService.discontinueMedicationOrder(301L, "Adverse reaction: GI distress");

        assertNotNull(result);
        assertEquals(OrderStatus.DISCONTINUED, result.getStatus());
        assertEquals("Adverse reaction: GI distress", result.getDiscontinueReason());
        assertNotNull(result.getDiscontinuedAt());
        assertEquals(doctor.getFullName(), result.getDiscontinuedByDoctorName());
        verify(orderRepository, times(1)).save(any(MedicationOrder.class));
    }
}
