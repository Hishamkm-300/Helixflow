package com.helixflow.modules.resource.ot.service;

import com.helixflow.common.exception.BusinessRuleException;
import com.helixflow.common.exception.ResourceNotFoundException;
import com.helixflow.modules.patient.entity.Visit;
import com.helixflow.modules.patient.repository.VisitRepository;
import com.helixflow.modules.resource.ot.dto.OtBookingDto;
import com.helixflow.modules.resource.ot.dto.OtBookingRequest;
import com.helixflow.modules.resource.ot.dto.OtRoomDto;
import com.helixflow.modules.resource.ot.entity.OperatingTheatre;
import com.helixflow.modules.resource.ot.entity.OtBooking;
import com.helixflow.modules.resource.ot.entity.OtBookingStatus;
import com.helixflow.modules.resource.ot.repository.OperatingTheatreRepository;
import com.helixflow.modules.resource.ot.repository.OtBookingRepository;
import com.helixflow.modules.resource.staff.entity.Staff;
import com.helixflow.modules.resource.staff.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtSchedulingService {

    private final OperatingTheatreRepository theatreRepository;
    private final OtBookingRepository bookingRepository;
    private final VisitRepository visitRepository;
    private final StaffRepository staffRepository;

    @Transactional(readOnly = true)
    public List<OtRoomDto> getTheatres() {
        return theatreRepository.findAll().stream()
                .map(OtRoomDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OtBookingDto> getBookings(Long theatreId, LocalDateTime start, LocalDateTime end) {
        LocalDateTime queryStart = start != null ? start : LocalDateTime.now().minusDays(1);
        LocalDateTime queryEnd = end != null ? end : LocalDateTime.now().plusDays(7);

        List<OtBooking> bookings;
        if (theatreId != null) {
            bookings = bookingRepository.findByTheatreIdAndScheduledStartBetweenOrderByScheduledStartAsc(theatreId, queryStart, queryEnd);
        } else {
            bookings = bookingRepository.findByScheduledStartBetweenOrderByScheduledStartAsc(queryStart, queryEnd);
        }
        return bookings.stream().map(OtBookingDto::fromEntity).collect(Collectors.toList());
    }

    /**
     * Reserves an Operating Theatre with Pessimistic Write Locking to prevent double bookings.
     * Enforces room collision checks (with 30-min buffer) and lead surgeon schedule exclusivity.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 5)
    public OtBookingDto createBooking(OtBookingRequest request) {
        if (!request.getScheduledEnd().isAfter(request.getScheduledStart())) {
            throw new BusinessRuleException("INVALID_TIME_RANGE", "Scheduled end time must be strictly after start time.");
        }

        OperatingTheatre theatre = theatreRepository.findById(request.getTheatreId())
                .orElseThrow(() -> new ResourceNotFoundException("Operating theatre not found: " + request.getTheatreId()));

        if (!theatre.isActive()) {
            throw new BusinessRuleException("THEATRE_INACTIVE", "Theatre " + theatre.getRoomNumber() + " is deactivated.");
        }

        Visit visit = visitRepository.findById(request.getVisitId())
                .orElseThrow(() -> new ResourceNotFoundException("Visit not found: " + request.getVisitId()));

        Staff surgeon = staffRepository.findById(request.getLeadSurgeonId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff surgeon not found: " + request.getLeadSurgeonId()));

        int bufferMinutes = theatre.getCleaningBufferMinutes() != null ? theatre.getCleaningBufferMinutes() : 30;
        LocalDateTime bufferedStart = request.getScheduledStart().minusMinutes(bufferMinutes);
        LocalDateTime bufferedEnd = request.getScheduledEnd().plusMinutes(bufferMinutes);

        // 1. Room collision check with pessimistic lock
        List<OtBooking> roomCollisions = bookingRepository.findOverlappingTheatreBookingsWithLock(
                theatre.getId(), bufferedStart, bufferedEnd);

        if (!roomCollisions.isEmpty()) {
            if (request.isEmergencyOverride()) {
                log.warn("Emergency override triggered for OT Room {}. Preempting active elective booking.", theatre.getRoomNumber());
                for (OtBooking conflict : roomCollisions) {
                    conflict.setStatus(OtBookingStatus.CANCELLED);
                    conflict.setNotes((conflict.getNotes() != null ? conflict.getNotes() + " | " : "") + "Preempted by Emergency Procedure");
                    bookingRepository.save(conflict);
                }
            } else {
                OtBooking conflict = roomCollisions.get(0);
                throw new BusinessRuleException("OT_ROOM_CONFLICT",
                        "Theatre " + theatre.getRoomNumber() + " is already reserved between "
                        + conflict.getScheduledStart() + " and " + conflict.getScheduledEnd()
                        + " (including " + bufferMinutes + "-min sanitation buffer).");
            }
        }

        // 2. Lead Surgeon collision check with pessimistic lock
        List<OtBooking> surgeonCollisions = bookingRepository.findOverlappingSurgeonBookingsWithLock(
                surgeon.getId(), request.getScheduledStart(), request.getScheduledEnd());

        if (!surgeonCollisions.isEmpty()) {
            OtBooking conflict = surgeonCollisions.get(0);
            throw new BusinessRuleException("SURGEON_CONFLICT",
                    "Lead surgeon " + surgeon.getUser().getFullName() + " is already booked in "
                    + conflict.getTheatre().getRoomNumber() + " from "
                    + conflict.getScheduledStart() + " to " + conflict.getScheduledEnd());
        }

        OtBooking booking = OtBooking.builder()
                .theatre(theatre)
                .visit(visit)
                .leadSurgeon(surgeon)
                .procedureName(request.getProcedureName())
                .scheduledStart(request.getScheduledStart())
                .scheduledEnd(request.getScheduledEnd())
                .status(OtBookingStatus.SCHEDULED)
                .notes(request.getNotes())
                .build();

        return OtBookingDto.fromEntity(bookingRepository.save(booking));
    }

    @Transactional
    public OtBookingDto cancelBooking(Long bookingId, String reason) {
        OtBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("OT Booking not found: " + bookingId));

        if (booking.getStatus() == OtBookingStatus.COMPLETED) {
            throw new BusinessRuleException("CANNOT_CANCEL_COMPLETED", "Cannot cancel an already completed surgical procedure.");
        }

        booking.setStatus(OtBookingStatus.CANCELLED);
        booking.setNotes((booking.getNotes() != null ? booking.getNotes() + " | " : "") + "Cancelled: " + (reason != null ? reason : "Clinician request"));

        return OtBookingDto.fromEntity(bookingRepository.save(booking));
    }
}
