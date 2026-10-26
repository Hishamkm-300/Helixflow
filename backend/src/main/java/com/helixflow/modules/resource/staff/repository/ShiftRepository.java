package com.helixflow.modules.resource.staff.repository;

import com.helixflow.modules.resource.staff.entity.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

    List<Shift> findByShiftDate(LocalDate shiftDate);

    List<Shift> findByStaffIdAndShiftDateBetweenAndCancelledFalse(Long staffId, LocalDate start, LocalDate end);

    @Query("SELECT s FROM Shift s WHERE s.shiftDate BETWEEN :startDate AND :endDate AND s.cancelled = false ORDER BY s.shiftDate ASC, s.startTime ASC")
    List<Shift> findRosterBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT s FROM Shift s WHERE s.staff.id = :staffId AND s.shiftDate = :date AND s.cancelled = false")
    List<Shift> findActiveShiftsOnDate(@Param("staffId") Long staffId, @Param("date") LocalDate date);

    @Query("SELECT s FROM Shift s WHERE s.staff.id = :staffId AND s.shiftDate >= :afterDate AND s.cancelled = false ORDER BY s.shiftDate DESC, s.endTime DESC")
    List<Shift> findRecentShifts(@Param("staffId") Long staffId, @Param("afterDate") LocalDate afterDate);
}
