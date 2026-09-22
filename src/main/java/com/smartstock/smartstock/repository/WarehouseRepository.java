package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    // Find warehouses by location keyword
    java.util.List<Warehouse> findByLocationContainingIgnoreCase(String location);
}