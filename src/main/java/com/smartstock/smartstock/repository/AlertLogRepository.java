package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.AlertLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertLogRepository extends JpaRepository<AlertLog, Long> {

    List<AlertLog> findByUserId(Long userId);

    List<AlertLog> findByAlertType(AlertLog.AlertType type);

    List<AlertLog> findByStatus(AlertLog.AlertStatus status);

    // Recent alerts for dashboard
    List<AlertLog> findTop20ByOrderBySentAtDesc();

    // Failed alerts — for retry logic
    List<AlertLog> findByStatusOrderBySentAtDesc(AlertLog.AlertStatus status);
}