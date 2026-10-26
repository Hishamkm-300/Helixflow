package com.helixflow.modules.resource.bed.service;

import com.helixflow.common.exception.BusinessRuleException;
import com.helixflow.common.exception.ResourceNotFoundException;
import com.helixflow.modules.patient.entity.Visit;
import com.helixflow.modules.patient.entity.VisitStatus;
import com.helixflow.modules.patient.repository.VisitRepository;
import com.helixflow.modules.resource.bed.dto.BedAdmissionDto;
import com.helixflow.modules.resource.bed.dto.BedAllocationDto;
import com.helixflow.modules.resource.bed.dto.BedCapacitySummaryDto;
import com.helixflow.modules.resource.bed.dto.BedDto;
import com.helixflow.modules.resource.bed.dto.WardBedSummaryDto;
import com.helixflow.modules.resource.bed.entity.*;
import com.helixflow.modules.resource.bed.repository.BedAdmissionRepository;
import com.helixflow.modules.resource.bed.repository.BedRepository;
import com.helixflow.modules.resource.bed.repository.WardRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BedManagementService {

    private final WardRepository wardRepository;
    private final BedRepository bedRepository;
    private final VisitRepository visitRepository;
    private final BedAdmissionRepository admissionRepository;

    @PostConstruct
    @Transactional
    public void bootstrapWardsAndBeds() {
        if (wardRepository.count() == 0) {
            log.info("Bootstrapping 4 standard hospital wards and bed inventories...");

            Ward edWard = wardRepository.save(Ward.builder()
                    .code("ED-ACUTE")
                    .name("Emergency Acute Unit")
                    .department("EMERGENCY")
                    .totalCapacity(10)
                    .build());

            Ward icuWard = wardRepository.save(Ward.builder()
                    .code("ICU-MAIN")
                    .name("Intensive Care Unit")
                    .department("ICU")
                    .totalCapacity(8)
                    .build());

            Ward medSurgWard = wardRepository.save(Ward.builder()
                    .code("MED-SURG-A")
                    .name("Medical-Surgical Ward A")
                    .department("GENERAL")
                    .totalCapacity(12)
                    .build());

            Ward generalWard = wardRepository.save(Ward.builder()
                    .code("GENERAL-WARD")
                    .name("General Medical Ward")
                    .department("GENERAL")
                    .totalCapacity(10)
                    .build());

            // 1. ED-ACUTE (10 beds: 1 ready, 3 occupied, 1 cleaning, 5 available)
            for (int i = 1; i <= 10; i++) {
                BedStatus status = BedStatus.AVAILABLE;
                if (i >= 2 && i <= 4) status = BedStatus.OCCUPIED;
                else if (i == 5) status = BedStatus.CLEANING;

                bedRepository.save(Bed.builder()
                        .ward(edWard)
                        .bedNumber("ED-" + String.format("%02d", i))
                        .bedType(BedType.MONITORED)
                        .status(status)
                        .build());
            }

            // 2. ICU-MAIN (8 beds: 2 occupied, 1 cleaning, 5 available)
            for (int i = 1; i <= 8; i++) {
                BedStatus status = BedStatus.AVAILABLE;
                if (i <= 2) status = BedStatus.OCCUPIED;
                else if (i == 3) status = BedStatus.CLEANING;

                bedRepository.save(Bed.builder()
                        .ward(icuWard)
                        .bedNumber("ICU-" + String.format("%02d", i))
                        .bedType(BedType.ICU_VENTILATOR)
                        .status(status)
                        .build());
            }

            // 3. MED-SURG-A (12 beds: 3 occupied, 1 cleaning, 8 available)
            for (int i = 1; i <= 12; i++) {
                BedStatus status = BedStatus.AVAILABLE;
                if (i <= 3) status = BedStatus.OCCUPIED;
                else if (i == 4) status = BedStatus.CLEANING;

                bedRepository.save(Bed.builder()
                        .ward(medSurgWard)
                        .bedNumber("MS-" + String.format("%02d", i))
                        .bedType(BedType.STANDARD)
                        .status(status)
                        .build());
            }

            // 4. GENERAL-WARD (10 beds: 2 occupied, 1 cleaning, 7 available)
            for (int i = 1; i <= 10; i++) {
                BedStatus status = BedStatus.AVAILABLE;
                if (i <= 2) status = BedStatus.OCCUPIED;
                else if (i == 3) status = BedStatus.CLEANING;

                bedRepository.save(Bed.builder()
                        .ward(generalWard)
                        .bedNumber("GW-" + String.format("%02d", i))
                        .bedType(BedType.STANDARD)
                        .status(status)
                        .build());
            }

            log.info("4 wards and 40 beds successfully initialized with varying operational statuses.");
        }
    }

    @Transactional(readOnly = true)
    public List<BedDto> getBeds(Long wardId, BedStatus status) {
        List<Bed> beds;
        if (wardId != null && status != null) {
            beds = bedRepository.findByWardIdAndStatus(wardId, status);
        } else if (wardId != null) {
            beds = bedRepository.findByWardId(wardId);
        } else if (status != null) {
            beds = bedRepository.findByStatus(status);
        } else {
            beds = bedRepository.findAll();
        }

        // Map active admissions for occupied beds
        Map<Long, BedAdmission> activeAdmissions = new HashMap<>();
        try {
            admissionRepository.findAll().stream()
                    .filter(a -> a.getDischargedAt() == null && a.getBed() != null)
                    .forEach(a -> activeAdmissions.put(a.getBed().getId(), a));
        } catch (Exception ex) {
            log.warn("Could not query active admissions: {}", ex.getMessage());
        }

        return beds.stream().map(bed -> {
            BedDto dto = BedDto.fromEntity(bed);
            BedAdmission admission = activeAdmissions.get(bed.getId());
            if (admission != null && admission.getVisit() != null) {
                dto.setCurrentVisitId(admission.getVisit().getId());
                dto.setCurrentVisitNumber(admission.getVisit().getVisitNumber());
                if (admission.getVisit().getPatient() != null) {
                    dto.setCurrentPatientName(
                            admission.getVisit().getPatient().getFirstName() + " " +
                            admission.getVisit().getPatient().getLastName()
                    );
                }
                dto.setOccupiedSince(admission.getAdmittedAt());
            }
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Allocates a bed to a patient visit using Pessimistic Write Locking
     * (SELECT ... FOR UPDATE) to guarantee atomic exclusivity.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 5)
    public BedAdmissionDto admitPatient(BedAllocationDto dto) {
        Bed bed = bedRepository.findByIdWithPessimisticLock(dto.getBedId())
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found with ID: " + dto.getBedId()));

        if (bed.getStatus() != BedStatus.AVAILABLE) {
            throw new BusinessRuleException("BED_NOT_AVAILABLE",
                    "Bed '" + bed.getBedNumber() + "' is currently " + bed.getStatus());
        }

        Visit visit = visitRepository.findById(dto.getVisitId())
                .orElseThrow(() -> new ResourceNotFoundException("Visit not found with ID: " + dto.getVisitId()));

        if (visit.getStatus() == VisitStatus.DISCHARGED) {
            throw new BusinessRuleException("VISIT_DISCHARGED", "Cannot allocate a bed to an already discharged visit.");
        }

        admissionRepository.findTopByVisitIdAndDischargedAtIsNull(visit.getId())
                .ifPresent(existing -> {
                    throw new BusinessRuleException("PATIENT_ALREADY_ADMITTED",
                            "Patient is already occupying bed '" + existing.getBed().getBedNumber() + "'");
                });

        bed.setStatus(BedStatus.OCCUPIED);
        bedRepository.save(bed);

        visit.setStatus(VisitStatus.ADMITTED);
        visitRepository.save(visit);

        BedAdmission admission = BedAdmission.builder()
                .bed(bed)
                .visit(visit)
                .notes(dto.getNotes())
                .admittedAt(Instant.now())
                .build();

        return BedAdmissionDto.fromEntity(admissionRepository.save(admission));
    }

    /**
     * Discharges patient and transitions bed to CLEANING.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 5)
    public BedAdmissionDto dischargePatient(Long bedId) {
        Bed bed = bedRepository.findByIdWithPessimisticLock(bedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found with ID: " + bedId));

        BedAdmission admission = admissionRepository.findTopByBedIdAndDischargedAtIsNull(bedId)
                .orElse(null);

        if (admission != null) {
            admission.setDischargedAt(Instant.now());
            admissionRepository.save(admission);

            Visit visit = admission.getVisit();
            if (visit != null) {
                visit.setStatus(VisitStatus.DISCHARGED);
                visit.setDischargeTime(Instant.now());
                visitRepository.save(visit);
            }
        }

        bed.setStatus(BedStatus.CLEANING);
        bedRepository.save(bed);

        return admission != null ? BedAdmissionDto.fromEntity(admission) :
                BedAdmissionDto.builder()
                        .bedId(bed.getId())
                        .bedNumber(bed.getBedNumber())
                        .dischargedAt(Instant.now())
                        .build();
    }

    /**
     * Marks a cleaned bed back to AVAILABLE.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 5)
    public BedDto markBedCleaned(Long bedId) {
        Bed bed = bedRepository.findByIdWithPessimisticLock(bedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found with ID: " + bedId));

        if (bed.getStatus() != BedStatus.CLEANING && bed.getStatus() != BedStatus.MAINTENANCE) {
            throw new BusinessRuleException("INVALID_BED_TRANSITION",
                    "Only beds in CLEANING or MAINTENANCE status can be marked AVAILABLE. Current: " + bed.getStatus());
        }

        bed.setStatus(BedStatus.AVAILABLE);
        return BedDto.fromEntity(bedRepository.save(bed));
    }

    @Transactional(readOnly = true)
    public BedCapacitySummaryDto getCapacitySummary() {
        long total = bedRepository.count();
        long available = bedRepository.countByStatus(BedStatus.AVAILABLE);
        long occupied = bedRepository.countByStatus(BedStatus.OCCUPIED);
        long cleaning = bedRepository.countByStatus(BedStatus.CLEANING);
        long maintenance = bedRepository.countByStatus(BedStatus.MAINTENANCE);

        double rate = total > 0 ? ((double) occupied / total) * 100.0 : 0.0;
        double roundedRate = Math.round(rate * 10.0) / 10.0;

        Map<String, Long> occupiedByWard = new HashMap<>();
        Map<String, Long> availableByWard = new HashMap<>();
        List<WardBedSummaryDto> wardSummaries = new ArrayList<>();

        for (Ward ward : wardRepository.findAll()) {
            long wOcc = bedRepository.countByWardIdAndStatus(ward.getId(), BedStatus.OCCUPIED);
            long wAvail = bedRepository.countByWardIdAndStatus(ward.getId(), BedStatus.AVAILABLE);
            long wClean = bedRepository.countByWardIdAndStatus(ward.getId(), BedStatus.CLEANING);
            long wMaint = bedRepository.countByWardIdAndStatus(ward.getId(), BedStatus.MAINTENANCE);
            long wTotal = wOcc + wAvail + wClean + wMaint;
            if (wTotal == 0) {
                wTotal = ward.getTotalCapacity();
            }
            double wRate = wTotal > 0 ? ((double) wOcc / wTotal) * 100.0 : 0.0;

            occupiedByWard.put(ward.getCode(), wOcc);
            availableByWard.put(ward.getCode(), wAvail);

            wardSummaries.add(WardBedSummaryDto.builder()
                    .wardId(ward.getId())
                    .wardCode(ward.getCode())
                    .wardName(ward.getName())
                    .department(ward.getDepartment())
                    .totalBeds(wTotal)
                    .availableBeds(wAvail)
                    .occupiedBeds(wOcc)
                    .cleaningBeds(wClean)
                    .maintenanceBeds(wMaint)
                    .occupancyRate(Math.round(wRate * 10.0) / 10.0)
                    .build());
        }

        return BedCapacitySummaryDto.builder()
                .totalBeds(total)
                .totalHospitalBeds(total)
                .availableBeds(available)
                .occupiedBeds(occupied)
                .cleaningBeds(cleaning)
                .maintenanceBeds(maintenance)
                .occupancyRate(roundedRate)
                .overallOccupancyRate(roundedRate)
                .wardSummaries(wardSummaries)
                .occupiedByWard(occupiedByWard)
                .availableByWard(availableByWard)
                .build();
    }
}
