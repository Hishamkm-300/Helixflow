package com.helixflow.modules.resource.medication.dto;

import com.helixflow.modules.resource.medication.entity.MedicationOrder;
import com.helixflow.modules.resource.medication.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicationOrderDto {

    private Long id;
    private Long visitId;
    private Long medicationId;
    private String medicationCode;
    private String genericName;
    private String brandName;
    private String medicationName;
    private String prescribedByDoctorName;
    private String prescribedByName;
    private String dose;
    private String route;
    private String frequency;
    private OrderStatus status;
    private boolean interactionOverridden;
    private String overrideReason;
    private Instant discontinuedAt;
    private String discontinueReason;
    private String discontinuedByDoctorName;
    private Instant createdAt;

    public static MedicationOrderDto fromEntity(MedicationOrder order) {
        String medName = order.getMedication().getGenericName();
        if (order.getMedication().getBrandName() != null && !order.getMedication().getBrandName().isBlank()) {
            medName += " (" + order.getMedication().getBrandName() + ")";
        }

        String doctorName = order.getPrescribedBy() != null ? order.getPrescribedBy().getFullName() : "Doctor";
        String disDoctorName = order.getDiscontinuedBy() != null ? order.getDiscontinuedBy().getFullName() : null;

        return MedicationOrderDto.builder()
                .id(order.getId())
                .visitId(order.getVisit().getId())
                .medicationId(order.getMedication().getId())
                .medicationCode(order.getMedication().getCode())
                .genericName(order.getMedication().getGenericName())
                .brandName(order.getMedication().getBrandName())
                .medicationName(medName)
                .prescribedByDoctorName(doctorName)
                .prescribedByName(doctorName)
                .dose(order.getDose())
                .route(order.getRoute())
                .frequency(order.getFrequency())
                .status(order.getStatus())
                .interactionOverridden(order.isInteractionOverridden())
                .overrideReason(order.getOverrideReason())
                .discontinuedAt(order.getDiscontinuedAt())
                .discontinueReason(order.getDiscontinueReason())
                .discontinuedByDoctorName(disDoctorName)
                .createdAt(order.getCreatedAt())
                .build();
    }
}
