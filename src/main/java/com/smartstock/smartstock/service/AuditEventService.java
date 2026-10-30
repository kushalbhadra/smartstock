package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.AuditEventResponse;
import com.smartstock.smartstock.entity.AuditEvent;
import com.smartstock.smartstock.repository.AuditEventRepository;
import com.smartstock.smartstock.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AuditEventService {

    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;

    public AuditEventService(
            AuditEventRepository auditEventRepository,
            UserRepository userRepository) {
        this.auditEventRepository = auditEventRepository;
        this.userRepository = userRepository;
    }

    public void record(
            String actorEmail,
            String actorName,
            String action,
            String entityType,
            Long entityId,
            String details) {
        auditEventRepository.save(AuditEvent.builder()
                .actorEmail(actorEmail)
                .actorName(userRepository.findByEmail(actorEmail)
                        .map(user -> user.getName())
                        .orElse(actorName))
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .build());
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> getRecent() {
        return auditEventRepository.findTop200ByOrderByCreatedAtDesc()
                .stream()
                .map(event -> AuditEventResponse.builder()
                        .id(event.getId())
                        .actorEmail(event.getActorEmail())
                        .actorName(event.getActorName())
                        .action(event.getAction())
                        .entityType(event.getEntityType())
                        .entityId(event.getEntityId())
                        .details(event.getDetails())
                        .createdAt(event.getCreatedAt())
                        .build())
                .toList();
    }
}
