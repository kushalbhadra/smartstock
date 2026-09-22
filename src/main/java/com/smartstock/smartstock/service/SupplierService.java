package com.smartstock.smartstock.service;

import com.smartstock.smartstock.entity.Supplier;
import com.smartstock.smartstock.exception.ResourceNotFoundException;
import com.smartstock.smartstock.repository.SupplierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class SupplierService {

    @Autowired
    private SupplierRepository supplierRepository;

    // Cache hit → returns from Redis instantly, no DB call
    // Cache miss → calls DB, stores in Redis, returns result
    @Cacheable(value = "suppliers", key = "'all'")
    @Transactional(readOnly = true)
    public List<Supplier> getAllSuppliers() {
        return supplierRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Supplier getSupplierById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Supplier", "id", id));
    }

    // After creating → old cached list is stale → evict it
    // Next getAllSuppliers call will fetch fresh from DB
    @CacheEvict(value = "suppliers", allEntries = true)
    public Supplier createSupplier(Supplier supplier) {
        return supplierRepository.save(supplier);
    }

    // After updating → evict cache so changes are visible
    @CacheEvict(value = "suppliers", allEntries = true)
    public Supplier updateSupplier(Long id, Supplier updated) {
        Supplier existing = getSupplierById(id);
        existing.setName(updated.getName());
        existing.setContactEmail(updated.getContactEmail());
        existing.setPhone(updated.getPhone());
        existing.setRating(updated.getRating());
        return supplierRepository.save(existing);
    }

    // After deleting → evict cache
    @CacheEvict(value = "suppliers", allEntries = true)
    public void deleteSupplier(Long id) {
        if (!supplierRepository.existsById(id)) {
            throw new ResourceNotFoundException("Supplier", "id", id);
        }
        supplierRepository.deleteById(id);
    }
}