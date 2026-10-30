package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findByNameContainingIgnoreCase(String name);

    boolean existsBySupplierCodeIgnoreCase(String supplierCode);

    // Find suppliers with rating above threshold
    List<Supplier> findByRatingGreaterThanEqual(Double rating);

    // Find top rated suppliers — for AI recommendations
    List<Supplier> findTop5ByOrderByRatingDesc();
}