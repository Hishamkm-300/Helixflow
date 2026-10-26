package com.helixflow.modules.resource.ot.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "operating_theatres")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperatingTheatre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_number", nullable = false, unique = true, length = 20)
    private String roomNumber;

    @Column(nullable = false, length = 50)
    private String name;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(name = "cleaning_buffer_minutes", nullable = false)
    private Integer cleaningBufferMinutes = 30;
}
