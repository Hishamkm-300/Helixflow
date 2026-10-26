package com.helixflow.modules.resource.medication.repository;

import com.helixflow.modules.resource.medication.entity.Medication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicationRepository extends JpaRepository<Medication, Long> {
    Optional<Medication> findByCode(String code);
    List<Medication> findByGenericNameContainingIgnoreCaseOrBrandNameContainingIgnoreCase(String generic, String brand);
    boolean existsByCode(String code);
}
