package com.helixflow.modules.resource.bed.repository;

import com.helixflow.modules.resource.bed.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WardRepository extends JpaRepository<Ward, Long> {
    Optional<Ward> findByCode(String code);
    boolean existsByCode(String code);
}
