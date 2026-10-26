package com.helixflow.modules.resource.staff.service;

import com.helixflow.common.exception.BusinessRuleException;
import com.helixflow.common.exception.ResourceNotFoundException;
import com.helixflow.modules.resource.bed.entity.Ward;
import com.helixflow.modules.resource.bed.repository.WardRepository;
import com.helixflow.modules.resource.staff.dto.ShiftAssignmentDto;
import com.helixflow.modules.resource.staff.dto.ShiftDto;
import com.helixflow.modules.resource.staff.dto.StaffDto;
import com.helixflow.modules.resource.staff.entity.Shift;
import com.helixflow.modules.resource.staff.entity.Staff;
import com.helixflow.modules.resource.staff.repository.ShiftRepository;
import com.helixflow.modules.resource.staff.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffSchedulingService {

    private final StaffRepository staffRepository;
    private final ShiftRepository shiftRepository;
    private final WardRepository wardRepository;

    @Transactional(readOnly = true)
    public List<StaffDto> getAllStaff() {
        return staffRepository.findAll().stream()
                .map(StaffDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ShiftDto> getRoster(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now().plusDays(14);
        return shiftRepository.findRosterBetween(start, end).stream()
                .map(ShiftDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Assigns a shift to a staff member, strictly enforcing:
     * 1. No direct time collisions on the same date.
     * 2. Mandatory 8-hour rest gap between consecutive shifts.
     * 3. Max weekly hours limit.
     */
    @Transactional
    public ShiftDto assignShift(ShiftAssignmentDto dto) {
        Staff staff = staffRepository.findById(dto.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found: " + dto.getStaffId()));

        Ward ward = null;
        if (dto.getWardId() != null) {
            ward = wardRepository.findById(dto.getWardId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ward not found: " + dto.getWardId()));
        }

        LocalDateTime shiftStart = LocalDateTime.of(dto.getShiftDate(), dto.getStartTime());
        LocalDateTime shiftEnd = LocalDateTime.of(dto.getShiftDate(), dto.getEndTime());
        if (!shiftEnd.isAfter(shiftStart)) {
            // Handle overnight shifts spanning into next day
            shiftEnd = shiftEnd.plusDays(1);
        }

        long shiftDurationHours = Duration.between(shiftStart, shiftEnd).toHours();

        // 1. Direct overlap check on same day
        List<Shift> existingShiftsOnDate = shiftRepository.findActiveShiftsOnDate(staff.getId(), dto.getShiftDate());
        for (Shift existing : existingShiftsOnDate) {
            LocalDateTime exStart = LocalDateTime.of(existing.getShiftDate(), existing.getStartTime());
            LocalDateTime exEnd = LocalDateTime.of(existing.getShiftDate(), existing.getEndTime());
            if (!exEnd.isAfter(exStart)) exEnd = exEnd.plusDays(1);

            if (shiftStart.isBefore(exEnd) && shiftEnd.isAfter(exStart)) {
                throw new BusinessRuleException("SHIFT_OVERLAP",
                        "Staff member already has an active shift on " + dto.getShiftDate()
                        + " (" + existing.getStartTime() + " - " + existing.getEndTime() + ")");
            }
        }

        // 2. Mandatory 8-hour break rule check
        List<Shift> recentShifts = shiftRepository.findRecentShifts(staff.getId(), dto.getShiftDate().minusDays(1));
        for (Shift prior : recentShifts) {
            LocalDateTime priorEnd = LocalDateTime.of(prior.getShiftDate(), prior.getEndTime());
            if (!priorEnd.isAfter(LocalDateTime.of(prior.getShiftDate(), prior.getStartTime()))) {
                priorEnd = priorEnd.plusDays(1);
            }

            if (shiftStart.isAfter(priorEnd)) {
                long restHours = Duration.between(priorEnd, shiftStart).toHours();
                if (restHours < 8) {
                    throw new BusinessRuleException("MANDATORY_BREAK_VIOLATION",
                            "Staff member requires at least 8 hours of rest between consecutive shifts. Prior shift ended at "
                            + priorEnd + " (only " + restHours + " hours rest).");
                }
            }
        }

        // 3. Weekly hours limit check
        LocalDate weekStart = dto.getShiftDate().minusDays(dto.getShiftDate().getDayOfWeek().getValue() - 1);
        LocalDate weekEnd = weekStart.plusDays(6);
        List<Shift> weeklyShifts = shiftRepository.findByStaffIdAndShiftDateBetweenAndCancelledFalse(staff.getId(), weekStart, weekEnd);

        long currentWeeklyHours = 0;
        for (Shift s : weeklyShifts) {
            LocalDateTime sStart = LocalDateTime.of(s.getShiftDate(), s.getStartTime());
            LocalDateTime sEnd = LocalDateTime.of(s.getShiftDate(), s.getEndTime());
            if (!sEnd.isAfter(sStart)) sEnd = sEnd.plusDays(1);
            currentWeeklyHours += Duration.between(sStart, sEnd).toHours();
        }

        if (currentWeeklyHours + shiftDurationHours > staff.getMaxWeeklyHours()) {
            throw new BusinessRuleException("MAX_WEEKLY_HOURS_EXCEEDED",
                    "Assigning this shift would result in " + (currentWeeklyHours + shiftDurationHours)
                    + " hours scheduled, exceeding the weekly maximum of " + staff.getMaxWeeklyHours() + " hours.");
        }

        Shift shift = Shift.builder()
                .staff(staff)
                .ward(ward)
                .shiftDate(dto.getShiftDate())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .shiftType(dto.getShiftType())
                .roleInShift(dto.getRoleInShift())
                .cancelled(false)
                .build();

        return ShiftDto.fromEntity(shiftRepository.save(shift));
    }

    @Transactional
    public ShiftDto cancelShift(Long shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found: " + shiftId));
        shift.setCancelled(true);
        return ShiftDto.fromEntity(shiftRepository.save(shift));
    }
}
