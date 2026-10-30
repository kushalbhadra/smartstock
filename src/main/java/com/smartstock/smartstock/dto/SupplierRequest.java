package com.smartstock.smartstock.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SupplierRequest {

    @Size(max = 40)
    private String supplierCode;

    @NotBlank
    @Size(max = 200)
    private String name;

    @Email
    @Size(max = 150)
    private String contactEmail;

    @Size(max = 20)
    private String phone;

    @Size(max = 150)
    private String contactPerson;

    @Size(max = 500)
    private String address;

    @DecimalMin("0.0")
    @DecimalMax("5.0")
    private BigDecimal rating;

    private Boolean active;
}
