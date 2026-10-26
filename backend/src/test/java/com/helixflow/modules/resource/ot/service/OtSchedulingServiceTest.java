package com.helixflow.modules.resource.ot.service;

import com.helixflow.common.exception.BusinessRuleException;
import com.helixflow.common.exception.ResourceNotFoundException;
import com.helixflow.modules.auth.entity.User;
import com.helixflow.modules.patient.entity.Patient;
import com.helixflow.modules.patient.entity.Visit;
import com.helixflow.modules.patient.entity.VisitStatus;
import com.helixflow.modules.patient.entity.VisitType;
import com.helixflow.modules.patient.repository.VisitRepository;
import com.helixflow.modules.resource.ot.dto.OtBookingDto;
import com.helixflow.modules.resource.ot.dto.OtBookingRequest;
import com.helixflow.modules.resource.ot.entity.OperatingTheatre;
import com.helixflow.modules.resource.ot.entity.OtBooking;
import com.helixflow.modules.resource.ot.entity.OtBookingStatus;
import com.helixflow.modules.resource.ot.repository.OperatingTheatreRepository;
import com.helixflow.modules.resource.ot.repository.OtBookingRepository;
import com.helixflow.modules.resource.staff.entity.Staff;
import com.helixflow.modules.resource.staff.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtSchedulingServiceTest {

    @Mock
    private OperatingTheatreRepository theatreRepository;

    @Mock
    private OtBookingRepository bookingRepository;

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private StaffRepository staffRepository;

    @InjectMocks
    private OtSchedulingService otSchedulingService;

    private OperatingTheatre theatre;
    private Visit visit;
    private Staff surgeon;
    private User surgeonUser;

    @BeforeEach
    void setUp() {
        theatre = OperatingTheatre.builder()
                .id(1L)
                .roomNumber("OT-01")
                .name("General Surgery Suite")
                .active(true)
                .cleaningBufferMinutes(30)
                .build();

        visit = Visit.builder()
                .id(100L)
                .visitNumber("ENC-100")
                .visitType(VisitType.EMERGENCY)
                .status(VisitStatus.IN_TREATMENT)
                .patient(Patient.builder().firstName("John").lastName("Doe").build())
                .build();

        surgeonUser = User.builder()
                .id(10L)
                .fullName("Dr. Sarah Adams, MD")
                .username("dr_adams")
                .build();

        surgeon = Staff.builder()
                .id(5L)
                .user(surgeonUser)
                .specialty("General Surgery")
                .maxWeeklyHours(48)
                .build();
    }

    @Test
    @DisplayName("Successfully schedule an OT booking when theatre and surgeon are available")
    void testCreateBooking_Success() {
        LocalDateTime start = LocalDateTime.now().plusHours(2);
        LocalDateTime end = start.plusHours(2);

        OtBookingRequest request = OtBookingRequest.builder()
                .theatreId(1L)
                .visitId(100L)
                .leadSurgeonId(5L)
                .procedureName("Appendectomy")
                .scheduledStart(start)
                .scheduledEnd(end)
                .emergencyOverride(false)
                .notes("Standard prep")
                .build();

        when(theatreRepository.findById(1L)).thenReturn(Optional.of(theatre));
        when(visitRepository.findById(100L)).thenReturn(Optional.of(visit));
        when(staffRepository.findById(5L)).thenReturn(Optional.of(surgeon));
        when(bookingRepository.findOverlappingTheatreBookingsWithLock(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(bookingRepository.findOverlappingSurgeonBookingsWithLock(eq(5L), eq(start), eq(end)))
                .thenReturn(Collections.emptyList());

        when(bookingRepository.save(any(OtBooking.class))).thenAnswer(invocation -> {
            OtBooking b = invocation.getArgument(0);
            b.setId(200L);
            return b;
        });

        OtBookingDto result = otSchedulingService.createBooking(request);

        assertNotNull(result);
        assertEquals(200L, result.getId());
        assertEquals("OT-01", result.getRoomNumber());
        assertEquals("Appendectomy", result.getProcedureName());
        assertEquals(OtBookingStatus.SCHEDULED, result.getStatus());
        verify(bookingRepository, times(1)).save(any(OtBooking.class));
    }

    @Test
    @DisplayName("Should throw BusinessRuleException when scheduled end is before or equal to start")
    void testCreateBooking_InvalidTimeRange() {
        LocalDateTime start = LocalDateTime.now().plusHours(2);
        LocalDateTime end = start.minusHours(1);

        OtBookingRequest request = OtBookingRequest.builder()
                .theatreId(1L)
                .visitId(100L)
                .leadSurgeonId(5L)
                .procedureName("Appendectomy")
                .scheduledStart(start)
                .scheduledEnd(end)
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> otSchedulingService.createBooking(request));
        assertEquals("INVALID_TIME_RANGE", ex.getCode());
    }

    @Test
    @DisplayName("Should throw BusinessRuleException when operating theatre is deactivated")
    void testCreateBooking_InactiveTheatre() {
        theatre.setActive(false);
        LocalDateTime start = LocalDateTime.now().plusHours(2);
        LocalDateTime end = start.plusHours(2);

        OtBookingRequest request = OtBookingRequest.builder()
                .theatreId(1L)
                .visitId(100L)
                .leadSurgeonId(5L)
                .procedureName("Appendectomy")
                .scheduledStart(start)
                .scheduledEnd(end)
                .build();

        when(theatreRepository.findById(1L)).thenReturn(Optional.of(theatre));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> otSchedulingService.createBooking(request));
        assertEquals("THEATRE_INACTIVE", ex.getCode());
    }

    @Test
    @DisplayName("Should detect room collision within buffer window and reject booking")
    void testCreateBooking_RoomConflictWithoutEmergency() {
        LocalDateTime start = LocalDateTime.now().plusHours(2);
        LocalDateTime end = start.plusHours(2);

        OtBooking conflict = OtBooking.builder()
                .id(150L)
                .theatre(theatre)
                .scheduledStart(start.minusMinutes(15))
                .scheduledEnd(end.minusMinutes(15))
                .status(OtBookingStatus.SCHEDULED)
                .build();

        OtBookingRequest request = OtBookingRequest.builder()
                .theatreId(1L)
                .visitId(100L)
                .leadSurgeonId(5L)
                .procedureName("Laparotomy")
                .scheduledStart(start)
                .scheduledEnd(end)
                .emergencyOverride(false)
                .build();

        when(theatreRepository.findById(1L)).thenReturn(Optional.of(theatre));
        when(visitRepository.findById(100L)).thenReturn(Optional.of(visit));
        when(staffRepository.findById(5L)).thenReturn(Optional.of(surgeon));
        when(bookingRepository.findOverlappingTheatreBookingsWithLock(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(conflict));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> otSchedulingService.createBooking(request));
        assertEquals("OT_ROOM_CONFLICT", ex.getCode());
    }

    @Test
    @DisplayName("Emergency override should preempt conflicting booking")
    void testCreateBooking_EmergencyPreemptsConflict() {
        LocalDateTime start = LocalDateTime.now().plusHours(2);
        LocalDateTime end = start.plusHours(2);

        OtBooking conflict = OtBooking.builder()
                .id(150L)
                .theatre(theatre)
                .scheduledStart(start.minusMinutes(15))
                .scheduledEnd(end.minusMinutes(15))
                .status(OtBookingStatus.SCHEDULED)
                .notes("Elective repair")
                .build();

        OtBookingRequest request = OtBookingRequest.builder()
                .theatreId(1L)
                .visitId(100L)
                .leadSurgeonId(5L)
                .procedureName("Emergency Vascular Hemostasis")
                .scheduledStart(start)
                .scheduledEnd(end)
                .emergencyOverride(true)
                .notes("Code Blue Trauma")
                .build();

        when(theatreRepository.findById(1L)).thenReturn(Optional.of(theatre));
        when(visitRepository.findById(100L)).thenReturn(Optional.of(visit));
        when(staffRepository.findById(5L)).thenReturn(Optional.of(surgeon));
        when(bookingRepository.findOverlappingTheatreBookingsWithLock(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(conflict));
        when(bookingRepository.findOverlappingSurgeonBookingsWithLock(eq(5L), eq(start), eq(end)))
                .thenReturn(Collections.emptyList());

        when(bookingRepository.save(any(OtBooking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OtBookingDto result = otSchedulingService.createBooking(request);

        assertNotNull(result);
        assertEquals(OtBookingStatus.CANCELLED, conflict.getStatus());
        assertTrue(conflict.getNotes().contains("Preempted by Emergency Procedure"));
        verify(bookingRepository, atLeast(2)).save(any(OtBooking.class));
    }

    @Test
    @DisplayName("Should detect lead surgeon schedule collision and reject booking")
    void testCreateBooking_SurgeonConflict() {
        LocalDateTime start = LocalDateTime.now().plusHours(2);
        LocalDateTime end = start.plusHours(2);

        OperatingTheatre theatre2 = OperatingTheatre.builder().id(2L).roomNumber("OT-02").build();
        OtBooking surgeonBusy = OtBooking.builder()
                .id(160L)
                .theatre(theatre2)
                .leadSurgeon(surgeon)
                .scheduledStart(start)
                .scheduledEnd(end)
                .status(OtBookingStatus.SCHEDULED)
                .build();

        OtBookingRequest request = OtBookingRequest.builder()
                .theatreId(1L)
                .visitId(100L)
                .leadSurgeonId(5L)
                .procedureName("Cholecystectomy")
                .scheduledStart(start)
                .scheduledEnd(end)
                .emergencyOverride(false)
                .build();

        when(theatreRepository.findById(1L)).thenReturn(Optional.of(theatre));
        when(visitRepository.findById(100L)).thenReturn(Optional.of(visit));
        when(staffRepository.findById(5L)).thenReturn(Optional.of(surgeon));
        when(bookingRepository.findOverlappingTheatreBookingsWithLock(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(bookingRepository.findOverlappingSurgeonBookingsWithLock(eq(5L), eq(start), eq(end)))
                .thenReturn(List.of(surgeonBusy));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> otSchedulingService.createBooking(request));
        assertEquals("SURGEON_CONFLICT", ex.getCode());
    }

    @Test
    @DisplayName("Cancel booking updates status to CANCELLED; completed procedures cannot be cancelled")
    void testCancelBooking() {
        OtBooking booking = OtBooking.builder()
                .id(300L)
                .theatre(theatre)
                .visit(visit)
                .leadSurgeon(surgeon)
                .status(OtBookingStatus.SCHEDULED)
                .scheduledStart(LocalDateTime.now().plusHours(1))
                .scheduledEnd(LocalDateTime.now().plusHours(3))
                .build();

        when(bookingRepository.findById(300L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(OtBooking.class))).thenAnswer(i -> i.getArgument(0));

        OtBookingDto cancelled = otSchedulingService.cancelBooking(300L, "Patient postponed");
        assertEquals(OtBookingStatus.CANCELLED, cancelled.getStatus());

        // Completed case
        booking.setStatus(OtBookingStatus.COMPLETED);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> otSchedulingService.cancelBooking(300L, "Too late"));
        assertEquals("CANNOT_CANCEL_COMPLETED", ex.getCode());
    }
}
