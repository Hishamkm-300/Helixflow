package com.helixflow.modules.resource.ot.entity;

import com.helixflow.modules.patient.entity.Visit;
import com.helixflow.modules.resource.staff.entity.Staff;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "ot_bookings",
    indexes = {
        @Index(name = "idx_ot_theatre_window", columnList = "theatre_id, status, scheduled_start, scheduled_end"),
        @Index(name = "idx_ot_surgeon_window", columnList = "lead_surgeon_id, status, scheduled_start, scheduled_end")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "theatre_id", nullable = false)
    private OperatingTheatre theatre;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lead_surgeon_id", nullable = false)
    private Staff leadSurgeon;

    @Column(name = "procedure_name", nullable = false, length = 150)
    private String procedureName;

    @Column(name = "scheduled_start", nullable = false)
    private LocalDateTime scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private LocalDateTime scheduledEnd;

    @Column(name = "actual_start")
    private LocalDateTime actualStart;

    @Column(name = "actual_end")
    private LocalDateTime actualEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OtBookingStatus status = OtBookingStatus.SCHEDULED;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
