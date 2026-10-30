package com.smartstock.smartstock.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SystemSettingsResponse {

    private String companyName;
    private String contactEmail;
    private String contactPhone;
    private Integer defaultLowStockThreshold;
}
