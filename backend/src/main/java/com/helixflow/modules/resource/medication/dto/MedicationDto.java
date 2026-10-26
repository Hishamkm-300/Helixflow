package com.helixflow.modules.resource.medication.dto;

import com.helixflow.modules.resource.medication.entity.Medication;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicationDto {

    private Long id;
    private String code;
    private String genericName;
    private String brandName;
    private String dosageForm;
    private String strength;
    private Double maxSingleDoseMg;
    private String unit;

    public static MedicationDto fromEntity(Medication med) {
        return MedicationDto.builder()
                .id(med.getId())
                .code(med.getCode())
                .genericName(med.getGenericName())
                .brandName(med.getBrandName())
                .dosageForm(med.getDosageForm())
                .strength(med.getStrength())
                .maxSingleDoseMg(med.getMaxSingleDoseMg())
                .unit(med.getUnit())
                .build();
    }
}
