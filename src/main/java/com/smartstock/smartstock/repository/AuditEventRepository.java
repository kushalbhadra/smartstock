package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findTop200ByOrderByCreatedAtDesc();

    @Query("""
            select case when count(event) > 0 then true else false end
            from AuditEvent event
            where upper(event.entityType) = 'PRODUCT'
              and event.entityId = :productId
            """)
    boolean existsProductHistory(@Param("productId") Long productId);

    @Query("""
            select distinct event.entityId from AuditEvent event
            where upper(event.entityType) = 'PRODUCT'
              and event.entityId in :productIds
            """)
    List<Long> findProductIdsWithAuditHistory(
            @Param("productIds") List<Long> productIds);
}
