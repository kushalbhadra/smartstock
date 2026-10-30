package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.SupportTicketRequest;
import com.smartstock.smartstock.dto.SupportTicketResponse;
import com.smartstock.smartstock.entity.SupportTicket;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.exception.BusinessException;
import com.smartstock.smartstock.exception.ResourceNotFoundException;
import com.smartstock.smartstock.repository.SupportTicketRepository;
import com.smartstock.smartstock.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class SupportTicketService {

    private final SupportTicketRepository ticketRepository;
    private final UserRepository userRepository;

    public SupportTicketService(
            SupportTicketRepository ticketRepository,
            UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    public SupportTicketResponse create(
            String email, SupportTicketRequest request) {
        User user = findUser(email);
        SupportTicket ticket = SupportTicket.builder()
                .createdBy(user)
                .subject(request.getSubject().trim())
                .message(request.getMessage().trim())
                .build();
        return toResponse(ticketRepository.save(ticket));
    }

    @Transactional(readOnly = true)
    public List<SupportTicketResponse> getForUser(String email) {
        User user = findUser(email);
        List<SupportTicket> tickets = user.getRole() == User.Role.ADMIN
                ? ticketRepository.findTop200ByOrderByCreatedAtDesc()
                : ticketRepository.findByCreatedByIdOrderByCreatedAtDesc(
                        user.getId());
        return tickets.stream().map(this::toResponse).toList();
    }

    public SupportTicketResponse updateStatus(
            Long id, SupportTicket.Status status) {
        SupportTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Support ticket", "id", id));
        if (status == null) {
            throw new BusinessException("Ticket status is required");
        }
        ticket.setStatus(status);
        return toResponse(ticketRepository.save(ticket));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "email", email));
    }

    private SupportTicketResponse toResponse(SupportTicket ticket) {
        return SupportTicketResponse.builder()
                .id(ticket.getId())
                .requesterName(ticket.getCreatedBy().getName())
                .requesterEmail(ticket.getCreatedBy().getEmail())
                .subject(ticket.getSubject())
                .message(ticket.getMessage())
                .status(ticket.getStatus())
                .createdAt(ticket.getCreatedAt())
                .build();
    }
}
