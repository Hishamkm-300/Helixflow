package com.helixflow.modules.patient.repository;

import com.helixflow.modules.patient.entity.TriageAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TriageAssessmentRepository extends JpaRepository<TriageAssessment, Long> {
    Optional<TriageAssessment> findTopByVisitIdOrderByTriageTimeDesc(Long visitId);

    @Query("SELECT t FROM TriageAssessment t WHERE t.visit.status IN ('TRIAGE_WAIT', 'IN_TREATMENT') ORDER BY t.esiLevel ASC, t.triageTime DESC")
    List<TriageAssessment> findActiveTriageQueue();

    @Query("SELECT COUNT(t) FROM TriageAssessment t WHERE t.visit.status IN ('TRIAGE_WAIT', 'IN_TREATMENT') AND t.esiLevel = :esiLevel")
    long countWaitingByEsiLevel(@Param("esiLevel") int esiLevel);
}
