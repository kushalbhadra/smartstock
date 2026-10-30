package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    Optional<Notification> findByRecipient_IdAndConditionKey(
            Long recipientId, String conditionKey);

    List<Notification> findByConditionKey(String conditionKey);

    List<Notification> findByRecipient_IdOrderByLastOccurredAtDesc(
            Long recipientId, Pageable pageable);

    long countByRecipient_IdAndReadAtIsNull(Long recipientId);

    Optional<Notification> findByIdAndRecipient_Id(
            Long id, Long recipientId);

    boolean existsByRecipient_Id(Long recipientId);

    @Query("""
            select n from Notification n
            where n.recipient.id = :recipientId
              and (:type is null or n.type = :type)
              and (:severity is null or n.severity = :severity)
              and (:active is null or n.active = :active)
              and (:unread is null
                   or (:unread = true and n.readAt is null)
                   or (:unread = false and n.readAt is not null))
            """)
    Page<Notification> searchForRecipient(
            @Param("recipientId") Long recipientId,
            @Param("type") Notification.Type type,
            @Param("severity") Notification.Severity severity,
            @Param("active") Boolean active,
            @Param("unread") Boolean unread,
            Pageable pageable);

    @Modifying
    @Query("""
            update Notification n
               set n.readAt = :readAt
             where n.recipient.id = :recipientId
               and n.readAt is null
            """)
    int markAllRead(
            @Param("recipientId") Long recipientId,
            @Param("readAt") LocalDateTime readAt);
}
