package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.AlertLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertLogRepository extends JpaRepository<AlertLog, Long> {

    List<AlertLog> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    List<AlertLog> findByAlertType(AlertLog.AlertType type);

    List<AlertLog> findByStatus(AlertLog.AlertStatus status);

    // Recent alerts for dashboard
    @EntityGraph(attributePaths = "user")
    List<AlertLog> findTop20ByOrderBySentAtDesc();

    // Failed alerts — for retry logic
    @EntityGraph(attributePaths = "user")
    List<AlertLog> findByStatusOrderBySentAtDesc(AlertLog.AlertStatus status);
}