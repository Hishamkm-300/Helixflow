package com.helixflow.modules.resource.staff.service;

import com.helixflow.common.exception.BusinessRuleException;
import com.helixflow.common.exception.ResourceNotFoundException;
import com.helixflow.modules.auth.entity.User;
import com.helixflow.modules.resource.bed.entity.Ward;
import com.helixflow.modules.resource.bed.repository.WardRepository;
import com.helixflow.modules.resource.staff.dto.ShiftAssignmentDto;
import com.helixflow.modules.resource.staff.dto.ShiftDto;
import com.helixflow.modules.resource.staff.entity.Shift;
import com.helixflow.modules.resource.staff.entity.ShiftType;
import com.helixflow.modules.resource.staff.entity.Staff;
import com.helixflow.modules.resource.staff.repository.ShiftRepository;
import com.helixflow.modules.resource.staff.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffSchedulingServiceTest {

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private WardRepository wardRepository;

    @InjectMocks
    private StaffSchedulingService staffSchedulingService;

    private Staff nurse;
    private Ward edWard;

    @BeforeEach
    void setUp() {
        User nurseUser = User.builder()
                .id(20L)
                .username("nurse_joy")
                .fullName("Nurse Joy Patel, RN")
                .build();

        nurse = Staff.builder()
                .id(1L)
                .user(nurseUser)
                .staffCode("STF-NUR-01")
                .department("EMERGENCY")
                .maxWeeklyHours(40)
                .build();

        edWard = Ward.builder()
                .id(1L)
                .code("ED-ACUTE")
                .name("Emergency Acute Care")
                .department("EMERGENCY")
                .build();
    }

    @Test
    @DisplayName("Successfully assign shift when all rest and hour constraints are met")
    void testAssignShift_Success() {
        LocalDate date = LocalDate.of(2026, 10, 26);
        ShiftAssignmentDto dto = ShiftAssignmentDto.builder()
                .staffId(1L)
                .wardId(1L)
                .shiftDate(date)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(16, 0))
                .shiftType(ShiftType.DAY)
                .roleInShift("Triage Nurse")
                .build();

        when(staffRepository.findById(1L)).thenReturn(Optional.of(nurse));
        when(wardRepository.findById(1L)).thenReturn(Optional.of(edWard));
        when(shiftRepository.findActiveShiftsOnDate(1L, date)).thenReturn(Collections.emptyList());
        when(shiftRepository.findRecentShifts(1L, date.minusDays(1))).thenReturn(Collections.emptyList());
        when(shiftRepository.findByStaffIdAndShiftDateBetweenAndCancelledFalse(eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> {
            Shift s = invocation.getArgument(0);
            s.setId(10L);
            return s;
        });

        ShiftDto result = staffSchedulingService.assignShift(dto);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals("Nurse Joy Patel, RN", result.getStaffName());
        assertEquals("ED-ACUTE", result.getWardCode());
        assertEquals(ShiftType.DAY, result.getShiftType());
    }

    @Test
    @DisplayName("Should reject assignment when shift directly overlaps another active shift on the same day")
    void testAssignShift_DirectOverlapConflict() {
        LocalDate date = LocalDate.of(2026, 10, 26);
        Shift existing = Shift.builder()
                .id(5L)
                .staff(nurse)
                .shiftDate(date)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(16, 0))
                .shiftType(ShiftType.DAY)
                .cancelled(false)
                .build();

        ShiftAssignmentDto dto = ShiftAssignmentDto.builder()
                .staffId(1L)
                .shiftDate(date)
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(20, 0))
                .shiftType(ShiftType.EVENING)
                .build();

        when(staffRepository.findById(1L)).thenReturn(Optional.of(nurse));
        when(shiftRepository.findActiveShiftsOnDate(1L, date)).thenReturn(List.of(existing));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> staffSchedulingService.assignShift(dto));
        assertEquals("SHIFT_OVERLAP", ex.getCode());
    }

    @Test
    @DisplayName("Should enforce mandatory 8-hour break between consecutive shifts")
    void testAssignShift_MandatoryBreakViolation() {
        LocalDate today = LocalDate.of(2026, 10, 26);
        LocalDate yesterday = today.minusDays(1);

        // Prior night shift ended at 04:00 AM on today's morning
        Shift priorShift = Shift.builder()
                .id(4L)
                .staff(nurse)
                .shiftDate(yesterday)
                .startTime(LocalTime.of(20, 0))
                .endTime(LocalTime.of(4, 0)) // ended at 4 AM today
                .shiftType(ShiftType.NIGHT)
                .cancelled(false)
                .build();

        // New shift requested to start at 08:00 AM today (only 4 hours break!)
        ShiftAssignmentDto dto = ShiftAssignmentDto.builder()
                .staffId(1L)
                .shiftDate(today)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(16, 0))
                .shiftType(ShiftType.DAY)
                .build();

        when(staffRepository.findById(1L)).thenReturn(Optional.of(nurse));
        when(shiftRepository.findActiveShiftsOnDate(1L, today)).thenReturn(Collections.emptyList());
        when(shiftRepository.findRecentShifts(1L, yesterday)).thenReturn(List.of(priorShift));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> staffSchedulingService.assignShift(dto));
        assertEquals("MANDATORY_BREAK_VIOLATION", ex.getCode());
        assertTrue(ex.getMessage().contains("at least 8 hours of rest"));
    }

    @Test
    @DisplayName("Should enforce max weekly scheduled hours limit")
    void testAssignShift_MaxWeeklyHoursExceeded() {
        LocalDate date = LocalDate.of(2026, 10, 28); // Wednesday
        LocalDate monday = LocalDate.of(2026, 10, 26);

        // Nurse already has 36 hours scheduled this week (max is 40)
        Shift s1 = Shift.builder().staff(nurse).shiftDate(monday).startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(19, 0)).cancelled(false).build(); // 12h
        Shift s2 = Shift.builder().staff(nurse).shiftDate(monday.plusDays(1)).startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(19, 0)).cancelled(false).build(); // 12h
        Shift s3 = Shift.builder().staff(nurse).shiftDate(monday.plusDays(2)).startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(19, 0)).cancelled(false).build(); // 12h
        // Total so far = 36h

        // Proposing another 12-hour shift (36 + 12 = 48 > 40 max)
        ShiftAssignmentDto dto = ShiftAssignmentDto.builder()
                .staffId(1L)
                .shiftDate(date.plusDays(1))
                .startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(19, 0))
                .shiftType(ShiftType.DAY)
                .build();

        when(staffRepository.findById(1L)).thenReturn(Optional.of(nurse));
        when(shiftRepository.findActiveShiftsOnDate(eq(1L), any(LocalDate.class))).thenReturn(Collections.emptyList());
        when(shiftRepository.findRecentShifts(eq(1L), any(LocalDate.class))).thenReturn(Collections.emptyList());
        when(shiftRepository.findByStaffIdAndShiftDateBetweenAndCancelledFalse(eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(s1, s2, s3));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> staffSchedulingService.assignShift(dto));
        assertEquals("MAX_WEEKLY_HOURS_EXCEEDED", ex.getCode());
        assertTrue(ex.getMessage().contains("exceeding the weekly maximum of 40"));
    }

    @Test
    @DisplayName("Cancel shift marks shift as cancelled")
    void testCancelShift() {
        Shift shift = Shift.builder()
                .id(15L)
                .staff(nurse)
                .shiftDate(LocalDate.now())
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(16, 0))
                .cancelled(false)
                .build();

        when(shiftRepository.findById(15L)).thenReturn(Optional.of(shift));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> i.getArgument(0));

        ShiftDto cancelled = staffSchedulingService.cancelShift(15L);
        assertTrue(cancelled.isCancelled());
    }
}
