package com.helixflow.modules.clinical.repository;

import com.helixflow.modules.clinical.entity.SoapOutput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SoapOutputRepository extends JpaRepository<SoapOutput, Long> {
    Optional<SoapOutput> findByClinicalNoteId(Long clinicalNoteId);
}
