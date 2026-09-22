package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.DemandForecast;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DemandForecastRepository
        extends JpaRepository<DemandForecast, Long> {

    @EntityGraph(attributePaths = "product")
    List<DemandForecast> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "product")
    List<DemandForecast> findByProductId(Long productId);

    @EntityGraph(attributePaths = "product")
    Optional<DemandForecast> findTopByProductIdOrderByCreatedAtDesc(Long productId);
}