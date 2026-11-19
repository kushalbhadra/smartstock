package com.smartstock.smartstock.service;

import com.smartstock.smartstock.entity.Supplier;
import com.smartstock.smartstock.entity.SupplierProduct;
import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.dto.SupplierProductRequest;
import com.smartstock.smartstock.dto.SupplierProductResponse;
import com.smartstock.smartstock.dto.SupplierProductAdminResponse;
import com.smartstock.smartstock.dto.SupplierRequest;
import com.smartstock.smartstock.exception.ResourceNotFoundException;
import com.smartstock.smartstock.repository.SupplierRepository;
import com.smartstock.smartstock.repository.PurchaseOrderRepository;
import com.smartstock.smartstock.repository.SupplierProductRepository;
import com.smartstock.smartstock.repository.ProductRepository;
import com.smartstock.smartstock.repository.StockRepository;
import com.smartstock.smartstock.repository.UserRepository;
import com.smartstock.smartstock.exception.BusinessException;
import com.smartstock.smartstock.validation.ContactDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

@Service
@Transactional
public class SupplierService {

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private SupplierProductRepository supplierProductRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private UserRepository userRepository;

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

    @Transactional(readOnly = true)
    public List<SupplierProductResponse> getSupplierProducts(
            Long supplierId, String email) {
        getSupplierById(supplierId);
        User user = findUser(email);
        return toVisibleProducts(
                supplierProductRepository
                        .findAllBySupplier_IdOrderByProduct_NameAsc(
                                supplierId),
                user);
    }

    @Transactional(readOnly = true)
    public List<SupplierProductAdminResponse> getAllSupplierProductMappings() {
        return supplierProductRepository.findAllWithSupplierAndProduct()
                .stream()
                .map(SupplierProductAdminResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SupplierProductResponse> getActiveSupplierProducts(
            Long supplierId, String email) {
        getSupplierById(supplierId);
        User user = findUser(email);
        return toVisibleProducts(
                supplierProductRepository
                        .findAllBySupplier_IdAndActiveTrueOrderByProduct_NameAsc(
                                supplierId),
                user);
    }

    public SupplierProductResponse addSupplierProduct(
            Long supplierId, SupplierProductRequest request) {
        Supplier supplier = getSupplierById(supplierId);
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product", "id", request.getProductId()));
        SupplierProduct association = supplierProductRepository
                .findBySupplier_IdAndProduct_Id(
                        supplierId, request.getProductId())
                .orElseGet(() -> SupplierProduct.builder()
                        .supplier(supplier)
                        .product(product)
                        .build());
        if (Boolean.TRUE.equals(association.getActive())
                && association.getId() != null) {
            throw new BusinessException(
                    "This product is already mapped to the supplier.");
        }
        if (request.getPurchasePrice() != null) {
            association.setPurchasePrice(request.getPurchasePrice());
        }
        if (request.getLeadTimeDays() != null) {
            association.setLeadTimeDays(request.getLeadTimeDays());
        }
        if (request.getSupplierProductCode() != null) {
            association.setSupplierProductCode(
                    request.getSupplierProductCode());
        }
        association.setActive(true);
        return SupplierProductResponse.from(
                supplierProductRepository.save(association));
    }

    public SupplierProductResponse setSupplierProductActive(
            Long supplierId, Long productId, boolean active) {
        SupplierProduct association = supplierProductRepository
                .findBySupplier_IdAndProduct_Id(supplierId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Supplier product", "productId", productId));
        association.setActive(active);
        return SupplierProductResponse.from(
                supplierProductRepository.save(association));
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
                .contactEmail(ContactDetails.normalizeEmail(
                        request.getContactEmail()))
                .phone(ContactDetails.normalizePhone(request.getPhone()))
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
            existing.setContactEmail(ContactDetails.normalizeEmail(
                    updated.getContactEmail()));
        }
        if (updated.getPhone() != null) {
            existing.setPhone(ContactDetails.normalizePhone(
                    updated.getPhone()));
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

    private List<SupplierProductResponse> toVisibleProducts(
            List<SupplierProduct> associations, User user) {
        if (user.getRole() != User.Role.WAREHOUSE_MGR) {
            return associations.stream()
                    .map(SupplierProductResponse::from)
                    .toList();
        }
        if (user.getWarehouse() == null) {
            return List.of();
        }
        Set<Long> stockedProductIds = new HashSet<>(
                stockRepository.findProductIdsByWarehouseId(
                        user.getWarehouse().getId()));
        return associations.stream()
                .filter(association -> stockedProductIds.contains(
                        association.getProduct().getId()))
                .map(SupplierProductResponse::from)
                .toList();
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User", "email", email));
    }
}