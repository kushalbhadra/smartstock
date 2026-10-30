package com.smartstock.smartstock.dto;

import com.smartstock.smartstock.entity.SupportTicket;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SupportTicketResponse {

    private Long id;
    private String requesterName;
    private String requesterEmail;
    private String subject;
    private String message;
    private SupportTicket.Status status;
    private LocalDateTime createdAt;
}
