package com.smartstock.smartstock.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SystemSettingsRequest {

    @NotBlank
    private String companyName;

    @Email
    private String contactEmail;

    private String contactPhone;

    @NotNull
    @Min(0)
    private Integer defaultLowStockThreshold;
}
