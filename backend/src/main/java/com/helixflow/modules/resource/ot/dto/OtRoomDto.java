package com.helixflow.modules.resource.ot.dto;

import com.helixflow.modules.resource.ot.entity.OperatingTheatre;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtRoomDto {

    private Long id;
    private String roomNumber;
    private String name;
    private boolean active;
    private Integer cleaningBufferMinutes;

    public static OtRoomDto fromEntity(OperatingTheatre ot) {
        return OtRoomDto.builder()
                .id(ot.getId())
                .roomNumber(ot.getRoomNumber())
                .name(ot.getName())
                .active(ot.isActive())
                .cleaningBufferMinutes(ot.getCleaningBufferMinutes())
                .build();
    }
}
