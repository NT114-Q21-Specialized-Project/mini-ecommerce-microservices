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
// ĐÃ XÓA @CrossOrigin để tránh lỗi Multiple Origin Not Allowed khi qua Gateway
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
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRoleHeader,
            @RequestBody Product product
    ) {
        // Kiểm tra nếu thiếu Header
        if (userIdHeader == null || userIdHeader.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Missing X-User-Id header");
        }

        // 1️⃣ Validate header
        try {
            UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Invalid X-User-Id header format");
        }

        // 2️⃣ Business + infra handling
        try {
            Product created = service.create(product, userRoleHeader);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(created);

        } catch (SecurityException e) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(e.getMessage());

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());

        } catch (IllegalStateException e) {
            // Infra error: unexpected downstream dependency issue
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
            @RequestHeader(value = "X-Internal-Caller", required = false) String caller,
            @RequestParam int quantity
    ) {
        if (!isAllowedInternalCaller(caller)) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body("Forbidden internal endpoint");
        }

        try {
            service.checkAndDecreaseStock(id, quantity);
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @PostMapping("/{id}/increase-stock")
    public ResponseEntity<?> increaseStock(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Caller", required = false) String caller,
            @RequestParam int quantity
    ) {
        if (!isAllowedInternalCaller(caller)) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body("Forbidden internal endpoint");
        }

        try {
            service.increaseStock(id, quantity);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    private boolean isAllowedInternalCaller(String caller) {
        if (caller == null || caller.isBlank()) {
            return false;
        }
        return "order-service".equalsIgnoreCase(caller) || "inventory-service".equalsIgnoreCase(caller);
    }
}
