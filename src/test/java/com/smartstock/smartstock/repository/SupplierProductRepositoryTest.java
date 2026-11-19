package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.Supplier;
import com.smartstock.smartstock.entity.SupplierProduct;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SupplierProductRepositoryTest {

    @Autowired
    private SupplierProductRepository supplierProductRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesAssociationAndFindsBothSides() {
        Supplier supplier = supplierRepository.saveAndFlush(
                Supplier.builder().name("Supplier A").build());
        Product product = productRepository.saveAndFlush(product("SKU-1"));

        SupplierProduct saved = supplierProductRepository.saveAndFlush(
                SupplierProduct.builder()
                        .supplier(supplier)
                        .product(product)
                        .purchasePrice(new BigDecimal("12.50"))
                        .leadTimeDays(5)
                        .supplierProductCode("SUP-SKU-1")
                        .build());

        assertTrue(supplierProductRepository.existsBySupplier_IdAndProduct_Id(
                supplier.getId(), product.getId()));
        assertEquals(saved.getId(), supplierProductRepository
                .findBySupplier_IdAndProduct_Id(
                        supplier.getId(), product.getId())
                .orElseThrow()
                .getId());
        assertEquals(1, supplierProductRepository
                .findAllBySupplier_IdOrderByProduct_NameAsc(
                        supplier.getId()).size());
        assertEquals(1, supplierProductRepository
                .findAllByProduct_IdOrderBySupplier_NameAsc(
                        product.getId()).size());

        SupplierProduct listed = supplierProductRepository
                .findAllWithSupplierAndProduct().get(0);
        assertEquals(supplier.getId(), listed.getSupplier().getId());
        assertEquals(product.getId(), listed.getProduct().getId());
    }

    @Test
    void allowsNullPurchasePriceAndLeadTime() {
        Supplier supplier = supplierRepository.saveAndFlush(
                Supplier.builder().name("Supplier A").build());
        Product product = productRepository.saveAndFlush(product("SKU-1"));

        SupplierProduct saved = supplierProductRepository.saveAndFlush(
                association(supplier, product));

        assertNull(saved.getPurchasePrice());
        assertNull(saved.getLeadTimeDays());
    }

    @Test
    void rejectsDuplicateSupplierProductPair() {
        Supplier supplier = supplierRepository.saveAndFlush(
                Supplier.builder().name("Supplier A").build());
        Product product = productRepository.saveAndFlush(product("SKU-1"));
        supplierProductRepository.saveAndFlush(
                association(supplier, product));

        assertThrows(DataIntegrityViolationException.class,
                () -> supplierProductRepository.saveAndFlush(
                        association(supplier, product)));
    }

    @Test
    void rejectsMissingSupplierReference() {
        Product product = productRepository.saveAndFlush(product("SKU-1"));
        Supplier missingSupplier = entityManager.getReference(
                Supplier.class, 999L);

        assertThrows(DataIntegrityViolationException.class,
                () -> supplierProductRepository.saveAndFlush(
                        association(missingSupplier, product)));
    }

    @Test
    void rejectsMissingProductReference() {
        Supplier supplier = supplierRepository.saveAndFlush(
                Supplier.builder().name("Supplier A").build());
        Product missingProduct = entityManager.getReference(
                Product.class, 999L);

        assertThrows(DataIntegrityViolationException.class,
                () -> supplierProductRepository.saveAndFlush(
                        association(supplier, missingProduct)));
    }

    @Test
    void inactiveAssociationRemainsStoredButIsExcludedFromActiveQueries() {
        Supplier supplier = supplierRepository.saveAndFlush(
                Supplier.builder().name("Supplier A").build());
        Product product = productRepository.saveAndFlush(product("SKU-1"));
        SupplierProduct association = supplierProductRepository.saveAndFlush(
                association(supplier, product));

        association.setActive(false);
        supplierProductRepository.saveAndFlush(association);

        assertEquals(1, supplierProductRepository
                .findAllBySupplier_IdOrderByProduct_NameAsc(
                        supplier.getId()).size());
        assertEquals(1, supplierProductRepository
                .findAllByProduct_IdOrderBySupplier_NameAsc(
                        product.getId()).size());
        assertTrue(supplierProductRepository
                .findAllBySupplier_IdAndActiveTrueOrderByProduct_NameAsc(
                        supplier.getId()).isEmpty());
        assertTrue(supplierProductRepository
                .findAllByProduct_IdAndActiveTrueOrderBySupplier_NameAsc(
                        product.getId()).isEmpty());
        assertFalse(supplierProductRepository
                .findBySupplier_IdAndProduct_Id(
                        supplier.getId(), product.getId())
                .orElseThrow()
                .getActive());
    }

    private static SupplierProduct association(
            Supplier supplier, Product product) {
        return SupplierProduct.builder()
                .supplier(supplier)
                .product(product)
                .build();
    }

    private static Product product(String sku) {
        return Product.builder()
                .name(sku)
                .sku(sku)
                .category("General")
                .unit("piece")
                .price(BigDecimal.ONE)
                .build();
    }
}
