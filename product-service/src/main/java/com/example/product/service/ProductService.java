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

    public Product create(Product product) {
        return repository.save(product);
    }

    public List<Product> findAll() {
        return repository.findAll();
    }

    public Product findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    // Check & decrease stock
    @Transactional
    public void checkAndDecreaseStock(UUID productId, int quantity) {

        int updatedRows = repository.decreaseStock(productId, quantity);

        if (updatedRows == 0) {
            throw new IllegalArgumentException("Product not found or insufficient stock");
        }
    }
}
