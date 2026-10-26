package com.helixflow.modules.resource.medication.repository;

import com.helixflow.modules.resource.medication.entity.MedicationOrder;
import com.helixflow.modules.resource.medication.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicationOrderRepository extends JpaRepository<MedicationOrder, Long> {
    List<MedicationOrder> findByVisitIdAndStatus(Long visitId, OrderStatus status);
    List<MedicationOrder> findByVisitIdOrderByCreatedAtDesc(Long visitId);
}
