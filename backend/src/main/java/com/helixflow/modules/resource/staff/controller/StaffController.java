package com.helixflow.modules.resource.staff.controller;

import com.helixflow.common.dto.ApiResponse;
import com.helixflow.modules.resource.staff.dto.ShiftAssignmentDto;
import com.helixflow.modules.resource.staff.dto.ShiftDto;
import com.helixflow.modules.resource.staff.dto.StaffDto;
import com.helixflow.modules.resource.staff.service.StaffSchedulingService;
import com.helixflow.security.RequireRole;
import com.helixflow.security.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffSchedulingService staffSchedulingService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StaffDto>>> getAllStaff() {
        return ResponseEntity.ok(ApiResponse.ok(staffSchedulingService.getAllStaff()));
    }

    @GetMapping("/shifts")
    public ResponseEntity<ApiResponse<List<ShiftDto>>> getRoster(
            @RequestParam(value = "start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(value = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(ApiResponse.ok(staffSchedulingService.getRoster(start, end)));
    }

    @PostMapping("/shifts")
    @RequireRole({Role.ADMIN})
    public ResponseEntity<ApiResponse<ShiftDto>> assignShift(@Valid @RequestBody ShiftAssignmentDto dto) {
        ShiftDto shift = staffSchedulingService.assignShift(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(shift));
    }

    @PutMapping("/shifts/{id}/cancel")
    @RequireRole({Role.ADMIN})
    public ResponseEntity<ApiResponse<ShiftDto>> cancelShift(@PathVariable("id") Long id) {
        ShiftDto shift = staffSchedulingService.cancelShift(id);
        return ResponseEntity.ok(ApiResponse.ok(shift));
    }
}
