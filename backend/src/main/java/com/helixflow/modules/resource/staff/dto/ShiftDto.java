package com.helixflow.modules.resource.staff.dto;

import com.helixflow.modules.resource.staff.entity.Shift;
import com.helixflow.modules.resource.staff.entity.ShiftType;
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
public class ShiftDto {

    private Long id;
    private Long staffId;
    private String staffCode;
    private String staffName;
    private Long wardId;
    private String wardCode;
    private LocalDate shiftDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private ShiftType shiftType;
    private String roleInShift;
    private boolean cancelled;

    public static ShiftDto fromEntity(Shift shift) {
        return ShiftDto.builder()
                .id(shift.getId())
                .staffId(shift.getStaff().getId())
                .staffCode(shift.getStaff().getStaffCode())
                .staffName(shift.getStaff().getUser().getFullName())
                .wardId(shift.getWard() != null ? shift.getWard().getId() : null)
                .wardCode(shift.getWard() != null ? shift.getWard().getCode() : "HOSPITAL-WIDE")
                .shiftDate(shift.getShiftDate())
                .startTime(shift.getStartTime())
                .endTime(shift.getEndTime())
                .shiftType(shift.getShiftType())
                .roleInShift(shift.getRoleInShift())
                .cancelled(shift.isCancelled())
                .build();
    }
}
