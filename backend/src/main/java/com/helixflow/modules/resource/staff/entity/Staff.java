package com.helixflow.modules.resource.staff.entity;

import com.helixflow.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "staff")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "staff_code", nullable = false, unique = true, length = 20)
    private String staffCode;

    @Column(nullable = false, length = 50)
    private String department;

    @Column(length = 50)
    private String specialty;

    @Column(nullable = false, length = 50)
    private String qualification;

    @Builder.Default
    @Column(name = "max_weekly_hours", nullable = false)
    private Integer maxWeeklyHours = 40;
}
