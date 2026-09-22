package com.smartstock.smartstock.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
//import java.util.List;

@Entity
@Table(name = "users")  // table name in PostgreSQL
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // NOT NULL, max 100 characters
    @Column(nullable = false, length = 100)
    private String name;

    // Email must be unique — no two users with same email
    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String password;

    // Store enum as string "ADMIN", "WAREHOUSE_MGR", "VIEWER"
    // not as number 0,1,2
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // Phone for WhatsApp alerts — optional
    @Column(length = 20)
    private String phone;

    // WhatsApp alert preference
    @Column(name = "whatsapp_alerts_enabled")
    @Builder.Default
    private Boolean whatsappAlertsEnabled = true;

    // Quiet hours — no alerts between these times
    @Column(name = "alert_quiet_start")
    private Integer alertQuietStart;  // e.g. 22 = 10PM

    @Column(name = "alert_quiet_end")
    private Integer alertQuietEnd;    // e.g. 7 = 7AM

    // Which warehouse this manager manages
    // null for ADMIN and VIEWER
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    // Auto-set when user is created
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // The Role enum — defined inside same file for simplicity
    public enum Role {
        ADMIN, WAREHOUSE_MGR, VIEWER
    }
}