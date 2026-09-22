package com.smartstock.smartstock.service;

import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.exception.ResourceNotFoundException;
import com.smartstock.smartstock.repository.WarehouseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class WarehouseService {

    @Autowired
    private WarehouseRepository warehouseRepository;

    // Get all warehouses
    @Cacheable(value = "warehouses", key = "'all'")
    @Transactional(readOnly = true)
    public List<Warehouse> getAllWarehouses() {
        return warehouseRepository.findAll();
    }

    // Get warehouse by ID
    @Transactional(readOnly = true)
    public Warehouse getWarehouseById(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Warehouse", "id", id));
    }

    // Create new warehouse
    @CacheEvict(value = "warehouses", allEntries = true)
    public Warehouse createWarehouse(Warehouse warehouse) {
        return warehouseRepository.save(warehouse);
    }

    // Update existing warehouse
    @CacheEvict(value = "warehouses", allEntries = true)
    public Warehouse updateWarehouse(Long id, Warehouse updated) {

        Warehouse existing = getWarehouseById(id);

        existing.setName(updated.getName());
        existing.setLocation(updated.getLocation());
        existing.setCapacity(updated.getCapacity());

        return warehouseRepository.save(existing);
    }

    // Delete warehouse
    @CacheEvict(value = "warehouses", allEntries = true)
    public void deleteWarehouse(Long id) {

        if (!warehouseRepository.existsById(id)) {
            throw new ResourceNotFoundException("Warehouse", "id", id);
        }

        warehouseRepository.deleteById(id);
    }
}