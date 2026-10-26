package com.helixflow.modules.clinical.repository;

import com.helixflow.modules.clinical.entity.ClinicalNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClinicalNoteRepository extends JpaRepository<ClinicalNote, Long> {
    List<ClinicalNote> findByVisitId(Long visitId);
    Optional<ClinicalNote> findTopByVisitIdOrderByCreatedAtDesc(Long visitId);
}
