package com.smartstock.smartstock.dto;

import com.smartstock.smartstock.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private User.Role role;
    private Long warehouseId;
    private String phone;
    private LocalDateTime createdAt;
}
