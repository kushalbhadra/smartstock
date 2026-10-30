package com.smartstock.smartstock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SupportTicketRequest {

    @NotBlank
    @Size(max = 160)
    private String subject;

    @NotBlank
    @Size(max = 4000)
    private String message;
}
