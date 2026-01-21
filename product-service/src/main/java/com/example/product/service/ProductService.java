package com.example.product.service;

import com.example.product.dto.UserRoleResponse;
import com.example.product.model.Product;
import com.example.product.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository repository;
    private final RestTemplate restTemplate;

    public ProductService(ProductRepository repository, RestTemplate restTemplate) {
        this.repository = repository;
        this.restTemplate = restTemplate;
    }

    // =========================
    // CREATE PRODUCT (SELLER ONLY)
    // =========================
    public Product create(Product product, UUID userId) {
        validateSeller(userId);
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
        int updatedRows = repository.decreaseStock(productId, quantity);
        if (updatedRows == 0) {
            throw new IllegalArgumentException("Product not found or insufficient stock");
        }
    }

    // =========================
    // INTERNAL: CHECK SELLER ROLE
    // =========================
    private void validateSeller(UUID userId) {
        // user-service là tên container trong docker-compose
        String url = "http://user-service:8080/users/" + userId + "/role";

        UserRoleResponse response;

        try {
            response = restTemplate.getForObject(url, UserRoleResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("User not found");
        } catch (HttpClientErrorException.Forbidden e) {
            throw new IllegalArgumentException("Access denied by user-service");
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("User service unavailable at " + url);
        } catch (Exception e) {
            throw new IllegalStateException("Error communicating with user-service: " + e.getMessage());
        }

        if (response == null || response.getRole() == null) {
            throw new IllegalStateException("Invalid response from user-service");
        }

        if (!"SELLER".equals(response.getRole())) {
            throw new IllegalArgumentException("Only SELLER can create product");
        }
    }
}