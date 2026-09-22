package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    List<Product> findByCategory(String category);

    boolean existsBySku(String sku);

    // Search by name containing keyword (case insensitive)
    List<Product> findByNameContainingIgnoreCase(String name);
}