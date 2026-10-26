package com.helixflow.modules.resource.outbreak.repository;

import com.helixflow.modules.resource.outbreak.entity.OutbreakAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutbreakAlertRepository extends JpaRepository<OutbreakAlert, Long> {
    List<OutbreakAlert> findByAcknowledgedFalseOrderByCreatedAtDesc();
    List<OutbreakAlert> findTop20ByOrderByCreatedAtDesc();
}
