package com.helixflow.modules.resource.bed.dto;

import com.helixflow.modules.resource.bed.entity.Bed;
import com.helixflow.modules.resource.bed.entity.BedStatus;
import com.helixflow.modules.resource.bed.entity.BedType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BedDto {

    private Long id;
    private Long wardId;
    private String wardCode;
    private String wardName;
    private String bedNumber;
    private BedType bedType;
    private BedStatus status;
    private String currentPatientName;
    private Long currentVisitId;
    private String currentVisitNumber;
    private Instant occupiedSince;
    private Instant lastCleanedAt;

    public static BedDto fromEntity(Bed bed) {
        return BedDto.builder()
                .id(bed.getId())
                .wardId(bed.getWard() != null ? bed.getWard().getId() : null)
                .wardCode(bed.getWard() != null ? bed.getWard().getCode() : null)
                .wardName(bed.getWard() != null ? bed.getWard().getName() : null)
                .bedNumber(bed.getBedNumber())
                .bedType(bed.getBedType())
                .status(bed.getStatus())
                .build();
    }
}
