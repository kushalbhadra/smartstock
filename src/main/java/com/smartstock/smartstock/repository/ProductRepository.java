package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    Optional<Product> findBySku(String sku);

    List<Product> findByCategory(String category);

    boolean existsBySku(String sku);

    boolean existsBySkuIgnoreCase(String sku);

    // Search by name containing keyword (case insensitive)
    List<Product> findByNameContainingIgnoreCase(String name);
}