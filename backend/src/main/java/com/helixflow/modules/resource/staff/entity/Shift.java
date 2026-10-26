package com.helixflow.modules.resource.staff.entity;

import com.helixflow.modules.resource.bed.entity.Ward;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(
    name = "shifts",
    indexes = {
        @Index(name = "idx_shift_calendar", columnList = "shift_date, start_time"),
        @Index(name = "idx_shift_staff", columnList = "staff_id, shift_date")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_id")
    private Ward ward;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift_type", nullable = false, length = 20)
    private ShiftType shiftType;

    @Column(name = "role_in_shift", nullable = false, length = 30)
    private String roleInShift;

    @Builder.Default
    @Column(name = "is_cancelled", nullable = false)
    private boolean cancelled = false;
}
