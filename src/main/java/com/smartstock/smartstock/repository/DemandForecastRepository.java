package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.DemandForecast;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DemandForecastRepository
        extends JpaRepository<DemandForecast, Long> {

    @EntityGraph(attributePaths = {"product", "warehouse"})
    List<DemandForecast> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"product", "warehouse"})
    List<DemandForecast> findTop5ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"product", "warehouse"})
    List<DemandForecast> findByWarehouseIdOrderByCreatedAtDesc(Long warehouseId);

    @EntityGraph(attributePaths = {"product", "warehouse"})
    @Query("""
            select forecast from DemandForecast forecast
            where forecast.id in (
                select max(candidate.id) from DemandForecast candidate
                group by candidate.product.id, candidate.warehouse.id
            )
            order by forecast.createdAt desc
            """)
    List<DemandForecast> findLatestForecastsByProductAndWarehouse();

    @EntityGraph(attributePaths = {"product", "warehouse"})
    @Query("""
            select forecast from DemandForecast forecast
            where forecast.warehouse.id = :warehouseId
              and forecast.id in (
                  select max(candidate.id) from DemandForecast candidate
                  where candidate.warehouse.id = :warehouseId
                  group by candidate.product.id, candidate.warehouse.id
              )
            order by forecast.createdAt desc
            """)
    List<DemandForecast> findLatestForecastsByWarehouseId(
            @Param("warehouseId") Long warehouseId);

    @EntityGraph(attributePaths = {"product", "warehouse"})
    List<DemandForecast> findTop5ByWarehouseIdOrderByCreatedAtDesc(
            Long warehouseId);

    @EntityGraph(attributePaths = "product")
    List<DemandForecast> findTop5ByProductIdInOrderByCreatedAtDesc(
            List<Long> productIds);

    @EntityGraph(attributePaths = "product")
    List<DemandForecast> findByProductId(Long productId);

    boolean existsByProductId(Long productId);

    @Query("""
            select distinct forecast.product.id from DemandForecast forecast
            where forecast.product.id in :productIds
            """)
    List<Long> findProductIdsWithForecasts(
            @Param("productIds") List<Long> productIds);

    @EntityGraph(attributePaths = {"product", "warehouse"})
    Optional<DemandForecast> findTopByProductIdOrderByCreatedAtDesc(Long productId);

    @EntityGraph(attributePaths = {"product", "warehouse"})
    Optional<DemandForecast>
    findTopByProductIdAndWarehouseIdOrderByCreatedAtDesc(
            Long productId, Long warehouseId);

    @EntityGraph(attributePaths = {"product", "warehouse"})
    List<DemandForecast>
    findTop500ByStatusAndAccuracyEvaluatedAtIsNullAndCreatedAtAfterAndWarehouseIsNotNullOrderByCreatedAtAsc(
            DemandForecast.ForecastStatus status, LocalDateTime createdAt);
}