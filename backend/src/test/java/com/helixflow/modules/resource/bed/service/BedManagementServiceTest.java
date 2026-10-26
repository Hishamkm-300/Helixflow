package com.helixflow.modules.resource.bed.service;

import com.helixflow.common.exception.BusinessRuleException;
import com.helixflow.modules.patient.entity.Patient;
import com.helixflow.modules.patient.entity.Visit;
import com.helixflow.modules.patient.entity.VisitStatus;
import com.helixflow.modules.patient.repository.VisitRepository;
import com.helixflow.modules.resource.bed.dto.BedAdmissionDto;
import com.helixflow.modules.resource.bed.dto.BedAllocationDto;
import com.helixflow.modules.resource.bed.dto.BedDto;
import com.helixflow.modules.resource.bed.entity.*;
import com.helixflow.modules.resource.bed.repository.BedAdmissionRepository;
import com.helixflow.modules.resource.bed.repository.BedRepository;
import com.helixflow.modules.resource.bed.repository.WardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BedManagementServiceTest {

    @Mock
    private WardRepository wardRepository;

    @Mock
    private BedRepository bedRepository;

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private BedAdmissionRepository admissionRepository;

    @InjectMocks
    private BedManagementService bedService;

    private Ward ward;
    private Bed availableBed;
    private Bed occupiedBed;
    private Visit activeVisit;

    @BeforeEach
    void setUp() {
        ward = Ward.builder().id(1L).code("ED-ACUTE").name("Emergency Ward").department("EMERGENCY").totalCapacity(8).build();

        availableBed = Bed.builder()
                .id(101L)
                .ward(ward)
                .bedNumber("ED-01")
                .bedType(BedType.MONITORED)
                .status(BedStatus.AVAILABLE)
                .build();

        occupiedBed = Bed.builder()
                .id(102L)
                .ward(ward)
                .bedNumber("ED-02")
                .bedType(BedType.MONITORED)
                .status(BedStatus.OCCUPIED)
                .build();

        Patient patient = Patient.builder().id(10L).mrn("SYN-123456").firstName("John").lastName("Doe").build();

        activeVisit = Visit.builder()
                .id(50L)
                .patient(patient)
                .visitNumber("ENC-999")
                .status(VisitStatus.TRIAGE_WAIT)
                .build();
    }

    @Test
    void shouldAdmitPatientSuccessfullyWithPessimisticLock() {
        BedAllocationDto dto = BedAllocationDto.builder()
                .bedId(101L)
                .visitId(50L)
                .notes("Admitted via emergency triage")
                .build();

        when(bedRepository.findByIdWithPessimisticLock(101L)).thenReturn(Optional.of(availableBed));
        when(visitRepository.findById(50L)).thenReturn(Optional.of(activeVisit));
        when(admissionRepository.findTopByVisitIdAndDischargedAtIsNull(50L)).thenReturn(Optional.empty());

        when(admissionRepository.save(any(BedAdmission.class))).thenAnswer(invocation -> {
            BedAdmission a = invocation.getArgument(0);
            a.setId(999L);
            return a;
        });

        BedAdmissionDto result = bedService.admitPatient(dto);

        assertNotNull(result);
        assertEquals(BedStatus.OCCUPIED, availableBed.getStatus());
        assertEquals(VisitStatus.ADMITTED, activeVisit.getStatus());
        verify(bedRepository).findByIdWithPessimisticLock(101L);
        verify(bedRepository).save(availableBed);
    }

    @Test
    void shouldRejectAllocationWhenBedIsNotAvailable() {
        BedAllocationDto dto = BedAllocationDto.builder().bedId(102L).visitId(50L).build();
        when(bedRepository.findByIdWithPessimisticLock(102L)).thenReturn(Optional.of(occupiedBed));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> bedService.admitPatient(dto));

        assertEquals("BED_NOT_AVAILABLE", ex.getCode());
        verify(bedRepository, never()).save(any());
        verify(admissionRepository, never()).save(any());
    }

    @Test
    void shouldTransitionBedToCleaningOnDischarge() {
        when(bedRepository.findByIdWithPessimisticLock(102L)).thenReturn(Optional.of(occupiedBed));

        BedAdmission activeAdmission = BedAdmission.builder()
                .id(88L)
                .bed(occupiedBed)
                .visit(activeVisit)
                .admittedAt(Instant.now().minusSeconds(3600))
                .build();
        when(admissionRepository.findTopByBedIdAndDischargedAtIsNull(102L)).thenReturn(Optional.of(activeAdmission));
        when(admissionRepository.save(any(BedAdmission.class))).thenAnswer(i -> i.getArgument(0));

        BedAdmissionDto result = bedService.dischargePatient(102L);

        assertNotNull(result);
        assertEquals(BedStatus.CLEANING, occupiedBed.getStatus());
        assertEquals(VisitStatus.DISCHARGED, activeVisit.getStatus());
        assertNotNull(activeAdmission.getDischargedAt());
    }

    @Test
    void shouldMarkCleanedBedAsAvailable() {
        Bed cleaningBed = Bed.builder().id(103L).ward(ward).bedNumber("ED-03").status(BedStatus.CLEANING).build();
        when(bedRepository.findByIdWithPessimisticLock(103L)).thenReturn(Optional.of(cleaningBed));
        when(bedRepository.save(cleaningBed)).thenReturn(cleaningBed);

        BedDto result = bedService.markBedCleaned(103L);

        assertEquals(BedStatus.AVAILABLE, result.getStatus());
        assertEquals(BedStatus.AVAILABLE, cleaningBed.getStatus());
    }
}
