package com.helixflow.modules.resource.bed.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BedAllocationDto {

    @NotNull(message = "Bed ID is required")
    private Long bedId;

    @NotNull(message = "Visit ID is required")
    private Long visitId;

    private String notes;
}
