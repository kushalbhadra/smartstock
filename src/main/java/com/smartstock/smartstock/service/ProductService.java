package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.ProductRequest;
import com.smartstock.smartstock.dto.ProductResponse;
import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.exception.BusinessException;
import com.smartstock.smartstock.exception.ResourceNotFoundException;
import com.smartstock.smartstock.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
// @Transactional means: wrap everything in a DB transaction
// If anything fails, roll back all changes
@Transactional
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    // Get all products
    @Cacheable(value = "products", key = "'all'")
    @Transactional(readOnly = true) // readOnly = faster for queries
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(this::toResponse)  // convert each entity to DTO
                .collect(Collectors.toList());
    }

    // Get single product by ID
    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Product", "id", id));
        return toResponse(product);
    }

    // Create new product
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(ProductRequest request) {

        // Business rule: SKU must be unique
        if (productRepository.existsBySku(request.getSku())) {
            throw new BusinessException(
                    "Product with SKU " + request.getSku() + " already exists"
            );
        }

        Product product = Product.builder()
                .name(request.getName())
                .sku(request.getSku().toUpperCase()) // always uppercase SKU
                .category(request.getCategory())
                .unit(request.getUnit())
                .price(request.getPrice())
                .build();

        Product saved = productRepository.save(product);
        return toResponse(saved);
    }

    // Update existing product
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse updateProduct(Long id, ProductRequest request) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Product", "id", id));

        // Check SKU uniqueness only if SKU changed
        if (!product.getSku().equals(request.getSku().toUpperCase()) &&
                productRepository.existsBySku(request.getSku())) {
            throw new BusinessException(
                    "Product with SKU " + request.getSku() + " already exists"
            );
        }

        product.setName(request.getName());
        product.setSku(request.getSku().toUpperCase());
        product.setCategory(request.getCategory());
        product.setUnit(request.getUnit());
        product.setPrice(request.getPrice());

        return toResponse(productRepository.save(product));
    }

    // Delete product
    @CacheEvict(value = "products", allEntries = true)
    public void deleteProduct(Long id) {

        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product", "id", id);
        }

        productRepository.deleteById(id);
    }

    // Search products
    @Transactional(readOnly = true)
    public List<ProductResponse> searchProducts(String keyword) {
        return productRepository
                .findByNameContainingIgnoreCase(keyword)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // Entity to DTO conversion — private helper method
    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .sku(product.getSku())
                .category(product.getCategory())
                .unit(product.getUnit())
                .price(product.getPrice())
                .createdAt(product.getCreatedAt())
                .build();
    }
}