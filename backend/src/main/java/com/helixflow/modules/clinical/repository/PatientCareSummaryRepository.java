package com.helixflow.modules.clinical.repository;

import com.helixflow.modules.clinical.entity.PatientCareSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientCareSummaryRepository extends JpaRepository<PatientCareSummary, Long> {
    Optional<PatientCareSummary> findByClinicalNoteId(Long clinicalNoteId);
    Optional<PatientCareSummary> findTopByVisitIdOrderByCreatedAtDesc(Long visitId);
    List<PatientCareSummary> findAllByVisitIdOrderByCreatedAtDesc(Long visitId);
}
