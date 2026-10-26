package com.helixflow.modules.resource.ot.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtBookingRequest {

    @NotNull(message = "Theatre ID is required")
    private Long theatreId;

    @NotNull(message = "Visit ID is required")
    private Long visitId;

    @NotNull(message = "Lead surgeon staff ID is required")
    private Long leadSurgeonId;

    @NotBlank(message = "Procedure name is required")
    private String procedureName;

    @NotNull(message = "Scheduled start time is required")
    @Future(message = "Scheduled start time must be in the future")
    private LocalDateTime scheduledStart;

    @NotNull(message = "Scheduled end time is required")
    @Future(message = "Scheduled end time must be in the future")
    private LocalDateTime scheduledEnd;

    private String notes;

    @Builder.Default
    private boolean emergencyOverride = false;
}
