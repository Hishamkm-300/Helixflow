package com.helixflow.modules.resource.bed.repository;

import com.helixflow.modules.resource.bed.entity.Bed;
import com.helixflow.modules.resource.bed.entity.BedStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BedRepository extends JpaRepository<Bed, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bed b WHERE b.id = :id")
    Optional<Bed> findByIdWithPessimisticLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bed b WHERE b.ward.id = :wardId AND b.status = 'AVAILABLE'")
    List<Bed> findAvailableBedsInWardWithLock(@Param("wardId") Long wardId);

    List<Bed> findByWardId(Long wardId);

    List<Bed> findByStatus(BedStatus status);

    List<Bed> findByWardIdAndStatus(Long wardId, BedStatus status);

    long countByStatus(BedStatus status);

    long countByWardIdAndStatus(Long wardId, BedStatus status);

    long countByWardId(Long wardId);
}
