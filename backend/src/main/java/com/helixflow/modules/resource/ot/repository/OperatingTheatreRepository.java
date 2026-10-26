package com.helixflow.modules.resource.ot.repository;

import com.helixflow.modules.resource.ot.entity.OperatingTheatre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OperatingTheatreRepository extends JpaRepository<OperatingTheatre, Long> {
    Optional<OperatingTheatre> findByRoomNumber(String roomNumber);
    List<OperatingTheatre> findByActiveTrue();
    boolean existsByRoomNumber(String roomNumber);
}
