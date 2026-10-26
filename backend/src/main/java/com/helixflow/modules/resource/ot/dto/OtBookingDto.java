package com.helixflow.modules.resource.ot.dto;

import com.helixflow.modules.resource.ot.entity.OtBooking;
import com.helixflow.modules.resource.ot.entity.OtBookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtBookingDto {

    private Long id;
    private Long theatreId;
    private String theatreName;
    private String roomNumber;
    private Long visitId;
    private String visitNumber;
    private String patientName;
    private Long leadSurgeonId;
    private String leadSurgeonName;
    private String procedureName;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private LocalDateTime actualStart;
    private LocalDateTime actualEnd;
    private OtBookingStatus status;
    private String notes;

    public static OtBookingDto fromEntity(OtBooking booking) {
        return OtBookingDto.builder()
                .id(booking.getId())
                .theatreId(booking.getTheatre().getId())
                .theatreName(booking.getTheatre().getName())
                .roomNumber(booking.getTheatre().getRoomNumber())
                .visitId(booking.getVisit().getId())
                .visitNumber(booking.getVisit().getVisitNumber())
                .patientName(booking.getVisit().getPatient().getFirstName() + " " + booking.getVisit().getPatient().getLastName())
                .leadSurgeonId(booking.getLeadSurgeon().getId())
                .leadSurgeonName(booking.getLeadSurgeon().getUser().getFullName())
                .procedureName(booking.getProcedureName())
                .scheduledStart(booking.getScheduledStart())
                .scheduledEnd(booking.getScheduledEnd())
                .actualStart(booking.getActualStart())
                .actualEnd(booking.getActualEnd())
                .status(booking.getStatus())
                .notes(booking.getNotes())
                .build();
    }
}
