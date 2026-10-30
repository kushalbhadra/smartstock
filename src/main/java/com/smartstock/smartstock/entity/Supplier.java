package com.smartstock.smartstock.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "suppliers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_code", unique = true, length = 40)
    @Size(max = 40)
    private String supplierCode;

    @Column(nullable = false, length = 200)
    @NotBlank
    @Size(max = 200)
    private String name;

    @Column(name = "contact_email", length = 150)
    @Email
    @Size(max = 150)
    private String contactEmail;

    @Column(length = 20)
    @Size(max = 20)
    private String phone;

    @Column(name = "contact_person", length = 150)
    @Size(max = 150)
    private String contactPerson;

    @Column(length = 500)
    @Size(max = 500)
    private String address;

    // Rating 0.0 to 5.0
    @Column(precision = 2, scale = 1)
    @DecimalMin("0.0")
    @DecimalMax("5.0")
    private BigDecimal rating;

    @Builder.Default
    @Column(nullable = false,
            columnDefinition = "boolean not null default true")
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}