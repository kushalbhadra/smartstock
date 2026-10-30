package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketRepository
        extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findByCreatedByIdOrderByCreatedAtDesc(Long userId);

    List<SupportTicket> findTop200ByOrderByCreatedAtDesc();

    boolean existsByCreatedById(Long userId);
}
