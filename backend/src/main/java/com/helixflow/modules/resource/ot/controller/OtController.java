package com.helixflow.modules.resource.ot.controller;

import com.helixflow.common.dto.ApiResponse;
import com.helixflow.modules.resource.ot.dto.OtBookingDto;
import com.helixflow.modules.resource.ot.dto.OtBookingRequest;
import com.helixflow.modules.resource.ot.dto.OtRoomDto;
import com.helixflow.modules.resource.ot.service.OtSchedulingService;
import com.helixflow.security.RequireRole;
import com.helixflow.security.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ot")
@RequiredArgsConstructor
public class OtController {

    private final OtSchedulingService otSchedulingService;

    @GetMapping("/theatres")
    public ResponseEntity<ApiResponse<List<OtRoomDto>>> getTheatres() {
        return ResponseEntity.ok(ApiResponse.ok(otSchedulingService.getTheatres()));
    }

    @GetMapping("/bookings")
    public ResponseEntity<ApiResponse<List<OtBookingDto>>> getBookings(
            @RequestParam(value = "theatreId", required = false) Long theatreId,
            @RequestParam(value = "start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(value = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(ApiResponse.ok(otSchedulingService.getBookings(theatreId, start, end)));
    }

    @PostMapping("/bookings")
    @RequireRole({Role.DOCTOR, Role.ADMIN})
    public ResponseEntity<ApiResponse<OtBookingDto>> createBooking(@Valid @RequestBody OtBookingRequest request) {
        OtBookingDto booking = otSchedulingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(booking));
    }

    @DeleteMapping("/bookings/{id}")
    @RequireRole({Role.DOCTOR, Role.ADMIN})
    public ResponseEntity<ApiResponse<OtBookingDto>> cancelBooking(
            @PathVariable("id") Long id,
            @RequestParam(value = "reason", required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.ok(otSchedulingService.cancelBooking(id, reason)));
    }
}
