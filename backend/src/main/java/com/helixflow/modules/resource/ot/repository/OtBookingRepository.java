package com.helixflow.modules.resource.ot.repository;

import com.helixflow.modules.resource.ot.entity.OtBooking;
import com.helixflow.modules.resource.ot.entity.OtBookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OtBookingRepository extends JpaRepository<OtBooking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT b FROM OtBooking b
        WHERE b.theatre.id = :theatreId
          AND b.status IN ('SCHEDULED', 'IN_PROGRESS')
          AND b.scheduledStart < :bufferedEnd
          AND b.scheduledEnd > :bufferedStart
    """)
    List<OtBooking> findOverlappingTheatreBookingsWithLock(
            @Param("theatreId") Long theatreId,
            @Param("bufferedStart") LocalDateTime bufferedStart,
            @Param("bufferedEnd") LocalDateTime bufferedEnd
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT b FROM OtBooking b
        WHERE b.leadSurgeon.id = :surgeonId
          AND b.status IN ('SCHEDULED', 'IN_PROGRESS')
          AND b.scheduledStart < :end
          AND b.scheduledEnd > :start
    """)
    List<OtBooking> findOverlappingSurgeonBookingsWithLock(
            @Param("surgeonId") Long surgeonId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    List<OtBooking> findByTheatreIdAndScheduledStartBetweenOrderByScheduledStartAsc(
            Long theatreId, LocalDateTime start, LocalDateTime end);

    List<OtBooking> findByScheduledStartBetweenOrderByScheduledStartAsc(
            LocalDateTime start, LocalDateTime end);

    List<OtBooking> findByStatus(OtBookingStatus status);
}
