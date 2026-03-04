package com.example.product.controller;

import com.example.product.dto.ProductCreateRequest;
import com.example.product.dto.ProductPageResponse;
import com.example.product.dto.ProductResponse;
import com.example.product.exception.BadRequestException;
import com.example.product.exception.ForbiddenException;
import com.example.product.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
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
    public ResponseEntity<ProductResponse> create(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRoleHeader,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        if (userIdHeader == null || userIdHeader.isEmpty()) {
            throw new BadRequestException("MISSING_USER_ID", "Missing X-User-Id header");
        }

        try {
            UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "INVALID_USER_ID",
                    "Invalid X-User-Id header format"
            );
        }

        ProductResponse created = service.create(request, userRoleHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // =========================
    // QUERY
    // =========================
    @GetMapping
    public ProductPageResponse getAll(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be greater than or equal to 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be greater than 0") @Max(value = 100, message = "size must be less than or equal to 100") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) @Positive(message = "minPrice must be greater than 0") Double minPrice,
            @RequestParam(required = false) @Positive(message = "maxPrice must be greater than 0") Double maxPrice,
            @RequestParam(required = false) @Min(value = 0, message = "minStock must be greater than or equal to 0") Integer minStock,
            @RequestParam(required = false) @Min(value = 0, message = "maxStock must be greater than or equal to 0") Integer maxStock
    ) {
        return service.findAll(page, size, sortBy, sortDir, name, minPrice, maxPrice, minStock, maxStock);
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable UUID id) {
        return service.findById(id);
    }

    // =========================
    // INTERNAL API (ORDER SERVICE)
    // =========================
    @PostMapping("/{id}/decrease-stock")
    public ResponseEntity<Void> decreaseStock(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Caller", required = false) String caller,
            @RequestParam @Min(value = 1, message = "quantity must be greater than 0") int quantity
    ) {
        if (!isAllowedInternalCaller(caller)) {
            throw new ForbiddenException("FORBIDDEN_INTERNAL_ENDPOINT", "Forbidden internal endpoint");
        }

        service.checkAndDecreaseStock(id, quantity);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/increase-stock")
    public ResponseEntity<Void> increaseStock(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Caller", required = false) String caller,
            @RequestParam @Min(value = 1, message = "quantity must be greater than 0") int quantity
    ) {
        if (!isAllowedInternalCaller(caller)) {
            throw new ForbiddenException("FORBIDDEN_INTERNAL_ENDPOINT", "Forbidden internal endpoint");
        }

        service.increaseStock(id, quantity);
        return ResponseEntity.ok().build();
    }

    private boolean isAllowedInternalCaller(String caller) {
        if (caller == null || caller.isBlank()) {
            return false;
        }
        return "order-service".equalsIgnoreCase(caller) || "inventory-service".equalsIgnoreCase(caller);
    }
}
