package com.smartstock.smartstock.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserProfileRequest {

    @NotBlank
    private String name;

    private String phone;
}
