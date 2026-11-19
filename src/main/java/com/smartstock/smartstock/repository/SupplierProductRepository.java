package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.SupplierProduct;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierProductRepository
        extends JpaRepository<SupplierProduct, Long> {

    @EntityGraph(attributePaths = {"supplier", "product"})
    @Query("""
            select association from SupplierProduct association
            order by association.supplier.name, association.product.name
            """)
    List<SupplierProduct> findAllWithSupplierAndProduct();

    boolean existsBySupplier_IdAndProduct_Id(
            Long supplierId, Long productId);

    boolean existsByProduct_Id(Long productId);

    @Query("""
            select distinct association.product.id
            from SupplierProduct association
            where association.product.id in :productIds
            """)
    List<Long> findProductIdsWithSupplierMappings(
            @Param("productIds") List<Long> productIds);

    Optional<SupplierProduct> findBySupplier_IdAndProduct_Id(
            Long supplierId, Long productId);

    List<SupplierProduct> findAllBySupplier_IdOrderByProduct_NameAsc(
            Long supplierId);

    List<SupplierProduct> findAllBySupplier_IdAndActiveTrueOrderByProduct_NameAsc(
            Long supplierId);

    List<SupplierProduct> findAllByProduct_IdOrderBySupplier_NameAsc(
            Long productId);

    List<SupplierProduct> findAllByProduct_IdAndActiveTrueOrderBySupplier_NameAsc(
            Long productId);
}
