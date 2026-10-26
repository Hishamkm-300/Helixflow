package com.helixflow.modules.resource.bed.repository;

import com.helixflow.modules.resource.bed.entity.BedAdmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BedAdmissionRepository extends JpaRepository<BedAdmission, Long> {
    Optional<BedAdmission> findTopByBedIdAndDischargedAtIsNull(Long bedId);
    Optional<BedAdmission> findTopByVisitIdAndDischargedAtIsNull(Long visitId);
}
