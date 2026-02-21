package com.example.product.service;

import com.example.product.model.Product;
import com.example.product.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository repository;
    
    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    // =========================
    // CREATE PRODUCT (SELLER / ADMIN)
    // =========================
    public Product create(Product product, String userRole) {
        validateProductInput(product);
        validateCreatorRole(userRole);
        return repository.save(product);
    }

    // =========================
    // QUERY
    // =========================
    public List<Product> findAll() {
        return repository.findAll();
    }

    public Product findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    // =========================
    // STOCK MANAGEMENT
    // =========================
    @Transactional
    public void checkAndDecreaseStock(UUID productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        int updatedRows = repository.decreaseStock(productId, quantity);
        if (updatedRows == 0) {
            throw new IllegalArgumentException("Product not found or insufficient stock");
        }
    }

    @Transactional
    public void increaseStock(UUID productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        int updatedRows = repository.increaseStock(productId, quantity);
        if (updatedRows == 0) {
            throw new IllegalArgumentException("Product not found");
        }
    }

    private void validateCreatorRole(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            throw new SecurityException("Missing user role");
        }

        if (!"SELLER".equalsIgnoreCase(userRole) && !"ADMIN".equalsIgnoreCase(userRole)) {
            throw new SecurityException("Only SELLER or ADMIN can create product");
        }
    }

    private void validateProductInput(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("Product payload is required");
        }

        if (product.getName() == null || product.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Product name is required");
        }

        if (product.getPrice() == null || product.getPrice() <= 0) {
            throw new IllegalArgumentException("Product price must be greater than 0");
        }

        if (product.getStock() == null || product.getStock() < 0) {
            throw new IllegalArgumentException("Product stock must be greater than or equal to 0");
        }

        product.setName(product.getName().trim());
    }
}
