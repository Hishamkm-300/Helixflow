package com.helixflow.modules.resource.staff.repository;

import com.helixflow.modules.resource.staff.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {
    Optional<Staff> findByStaffCode(String staffCode);
    Optional<Staff> findByUserId(Long userId);
    List<Staff> findByDepartment(String department);
    boolean existsByStaffCode(String staffCode);
}
