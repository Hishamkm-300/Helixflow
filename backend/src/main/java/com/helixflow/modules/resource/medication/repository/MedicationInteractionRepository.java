package com.helixflow.modules.resource.medication.repository;

import com.helixflow.modules.resource.medication.entity.MedicationInteractionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MedicationInteractionRepository extends JpaRepository<MedicationInteractionRule, Long> {

    @Query("""
        SELECT r FROM MedicationInteractionRule r
        WHERE (r.primaryMed.id = :med1 AND r.interactingMed.id = :med2)
           OR (r.primaryMed.id = :med2 AND r.interactingMed.id = :med1)
    """)
    Optional<MedicationInteractionRule> findInteractionBetween(
            @Param("med1") Long med1,
            @Param("med2") Long med2
    );

    @Query("""
        SELECT r FROM MedicationInteractionRule r
        WHERE r.primaryMed.id IN :medIds AND r.interactingMed.id IN :medIds
    """)
    List<MedicationInteractionRule> findInteractionsAmong(@Param("medIds") List<Long> medIds);
}
