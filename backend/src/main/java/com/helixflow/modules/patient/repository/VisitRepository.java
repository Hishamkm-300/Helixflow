package com.helixflow.modules.patient.repository;

import com.helixflow.modules.patient.entity.Visit;
import com.helixflow.modules.patient.entity.VisitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VisitRepository extends JpaRepository<Visit, Long> {
    Optional<Visit> findByVisitNumber(String visitNumber);
    List<Visit> findByStatus(VisitStatus status);
    List<Visit> findByPatientId(Long patientId);

    @Query("SELECT v FROM Visit v WHERE v.status IN ('TRIAGE_WAIT', 'IN_TREATMENT') ORDER BY v.arrivalTime ASC")
    List<Visit> findActiveEdVisits();

    @Query("SELECT v FROM Visit v WHERE v.status IN ('TRIAGE_WAIT', 'IN_TREATMENT', 'ADMITTED') ORDER BY v.arrivalTime DESC")
    List<Visit> findActiveEncounterVisits();

    long countByStatus(VisitStatus status);
}
