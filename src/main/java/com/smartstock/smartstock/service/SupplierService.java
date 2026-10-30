package com.smartstock.smartstock.service;

import com.smartstock.smartstock.entity.Supplier;
import com.smartstock.smartstock.dto.SupplierRequest;
import com.smartstock.smartstock.exception.ResourceNotFoundException;
import com.smartstock.smartstock.repository.SupplierRepository;
import com.smartstock.smartstock.repository.PurchaseOrderRepository;
import com.smartstock.smartstock.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class SupplierService {

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

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

    public Supplier createSupplier(SupplierRequest request) {
        String supplierCode = normalizeCode(request.getSupplierCode());
        if (supplierCode != null
                && supplierRepository.existsBySupplierCodeIgnoreCase(
                        supplierCode)) {
            throw new BusinessException(
                    "A supplier with this supplier code already exists.");
        }
        Supplier supplier = Supplier.builder()
                .supplierCode(supplierCode)
                .name(request.getName().trim())
                .contactEmail(request.getContactEmail())
                .phone(request.getPhone())
                .contactPerson(request.getContactPerson())
                .address(request.getAddress())
                .rating(request.getRating())
                .active(request.getActive() == null
                        ? true : request.getActive())
                .build();
        return supplierRepository.save(supplier);
    }

    public Supplier updateSupplier(Long id, SupplierRequest updated) {
        Supplier existing = getSupplierById(id);
        String supplierCode = normalizeCode(updated.getSupplierCode());
        if (supplierCode != null
                && !supplierCode.equalsIgnoreCase(
                        existing.getSupplierCode() == null
                                ? "" : existing.getSupplierCode())
                && supplierRepository.existsBySupplierCodeIgnoreCase(
                        supplierCode)) {
            throw new BusinessException(
                    "A supplier with this supplier code already exists.");
        }
        if (supplierCode != null) {
            existing.setSupplierCode(supplierCode);
        }
        existing.setName(updated.getName());
        if (updated.getContactEmail() != null) {
            existing.setContactEmail(updated.getContactEmail());
        }
        if (updated.getPhone() != null) {
            existing.setPhone(updated.getPhone());
        }
        if (updated.getContactPerson() != null) {
            existing.setContactPerson(updated.getContactPerson());
        }
        if (updated.getAddress() != null) {
            existing.setAddress(updated.getAddress());
        }
        if (updated.getRating() != null) {
            existing.setRating(updated.getRating());
        }
        existing.setActive(updated.getActive() == null
                ? existing.getActive() : updated.getActive());
        return supplierRepository.save(existing);
    }

    public void deleteSupplier(Long id) {
        getSupplierById(id);
        if (purchaseOrderRepository.existsBySupplierId(id)) {
            throw new BusinessException(
                    "A supplier referenced by purchase orders cannot be deleted.");
        }
        supplierRepository.deleteById(id);
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase();
    }
}