package com.example.product.controller;

import com.example.product.model.Product;
import com.example.product.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    // =========================
    // CREATE PRODUCT (SELLER ONLY)
    // =========================
    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestBody Product product
    ) {
        UUID userId;

        // 1️⃣ Validate header
        try {
            userId = UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Invalid X-User-Id header");
        }

        // 2️⃣ Business + infra handling
        try {
            Product created = service.create(product, userId);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(created);

        } catch (IllegalArgumentException e) {
            // Business error: role invalid / user not found
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(e.getMessage());

        } catch (IllegalStateException e) {
            // Infra error: user-service unavailable
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(e.getMessage());
        }
    }

    // =========================
    // QUERY
    // =========================
    @GetMapping
    public List<Product> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(service.findById(id));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(e.getMessage());
        }
    }

    // =========================
    // INTERNAL API (ORDER SERVICE)
    // =========================
    @PostMapping("/{id}/decrease-stock")
    public ResponseEntity<?> decreaseStock(
            @PathVariable UUID id,
            @RequestParam int quantity
    ) {
        try {
            service.checkAndDecreaseStock(id, quantity);
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }
}
