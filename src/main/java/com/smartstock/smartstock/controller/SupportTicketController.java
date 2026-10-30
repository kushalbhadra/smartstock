package com.smartstock.smartstock.controller;

import com.smartstock.smartstock.dto.SupportTicketRequest;
import com.smartstock.smartstock.dto.SupportTicketResponse;
import com.smartstock.smartstock.entity.SupportTicket;
import com.smartstock.smartstock.service.SupportTicketService;
import com.smartstock.smartstock.service.AuditEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/support/tickets")
public class SupportTicketController {

    private final SupportTicketService ticketService;
    private final AuditEventService auditEventService;

    public SupportTicketController(
            SupportTicketService ticketService,
            AuditEventService auditEventService) {
        this.ticketService = ticketService;
        this.auditEventService = auditEventService;
    }

    @GetMapping
    public ResponseEntity<List<SupportTicketResponse>> getForUser(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                ticketService.getForUser(userDetails.getUsername()));
    }

    @PostMapping
    public ResponseEntity<SupportTicketResponse> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SupportTicketRequest request) {
        SupportTicketResponse response = ticketService.create(
                userDetails.getUsername(), request);
        auditEventService.record(userDetails.getUsername(),
                userDetails.getUsername(), "CREATED", "SUPPORT_TICKET",
                response.getId(), "Opened support ticket: "
                        + response.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SupportTicketResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam SupportTicket.Status status,
            @AuthenticationPrincipal UserDetails userDetails) {
        SupportTicketResponse response =
                ticketService.updateStatus(id, status);
        auditEventService.record(userDetails.getUsername(),
                userDetails.getUsername(), "UPDATED_STATUS",
                "SUPPORT_TICKET", id,
                "Marked support ticket " + id + " as " + status);
        return ResponseEntity.ok(response);
    }
}
