package com.helixflow.modules.resource.staff.dto;

import com.helixflow.modules.resource.staff.entity.ShiftType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftAssignmentDto {

    @NotNull(message = "Staff ID is required")
    private Long staffId;

    private Long wardId;

    @NotNull(message = "Shift date is required")
    private LocalDate shiftDate;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;

    @NotNull(message = "Shift type is required")
    private ShiftType shiftType;

    @NotNull(message = "Role in shift is required")
    private String roleInShift;
}
