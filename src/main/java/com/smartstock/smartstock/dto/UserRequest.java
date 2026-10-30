package com.smartstock.smartstock.dto;

import com.smartstock.smartstock.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;

    private String password;
    private User.Role role;
    private Long warehouseId;
    private String phone;
}
