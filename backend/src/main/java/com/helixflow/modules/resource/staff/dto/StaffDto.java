package com.helixflow.modules.resource.staff.dto;

import com.helixflow.modules.resource.staff.entity.Staff;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffDto {

    private Long id;
    private Long userId;
    private String staffCode;
    private String fullName;
    private String email;
    private String department;
    private String specialty;
    private String qualification;
    private Integer maxWeeklyHours;

    public static StaffDto fromEntity(Staff staff) {
        return StaffDto.builder()
                .id(staff.getId())
                .userId(staff.getUser().getId())
                .staffCode(staff.getStaffCode())
                .fullName(staff.getUser().getFullName())
                .email(staff.getUser().getEmail())
                .department(staff.getDepartment())
                .specialty(staff.getSpecialty())
                .qualification(staff.getQualification())
                .maxWeeklyHours(staff.getMaxWeeklyHours())
                .build();
    }
}
