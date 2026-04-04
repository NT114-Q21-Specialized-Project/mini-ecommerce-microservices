package com.example.product.service;

import com.example.product.dto.ProductCreateRequest;
import com.example.product.dto.ProductPatchRequest;
import com.example.product.dto.ProductPageResponse;
import com.example.product.dto.ProductResponse;
import com.example.product.exception.BadRequestException;
import com.example.product.exception.ConflictException;
import com.example.product.exception.ForbiddenException;
import com.example.product.exception.NotFoundException;
import com.example.product.model.Product;
import com.example.product.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
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
    public ProductResponse create(ProductCreateRequest request, String userRole) {
        Product product = new Product();
        applyFullUpdate(product, request, userRole);

        return toResponse(repository.save(product));
    }

    @Transactional
    public ProductResponse replace(UUID id, ProductCreateRequest request, String userRole) {
        Product product = getProductOrThrow(id);
        applyFullUpdate(product, request, userRole);
        return toResponse(repository.save(product));
    }

    @Transactional
    public ProductResponse patch(UUID id, ProductPatchRequest request, String userRole) {
        validateProductManagerRole(userRole);

        boolean hasChanges = request.getName() != null
                || request.getPrice() != null
                || request.getStock() != null;
        if (!hasChanges) {
            throw new BadRequestException("INVALID_PATCH_REQUEST", "At least one field must be provided");
        }

        Product product = getProductOrThrow(id);
        if (request.getName() != null) {
            product.setName(normalizeName(request.getName()));
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getStock() != null) {
            product.setStock(request.getStock());
        }

        return toResponse(repository.save(product));
    }

    @Transactional
    public void delete(UUID id, String userRole) {
        validateProductManagerRole(userRole);
        Product product = getProductOrThrow(id);
        repository.delete(product);
        repository.flush();
    }

    // =========================
    // QUERY
    // =========================
    public ProductPageResponse findAll(
            int page,
            int size,
            String sortBy,
            String sortDir,
            String name,
            Double minPrice,
            Double maxPrice,
            Integer minStock,
            Integer maxStock
    ) {
        validateRange(minPrice, maxPrice, "price");
        validateRange(minStock, maxStock, "stock");

        String normalizedSortBy = normalizeSortBy(sortBy);
        Sort.Direction direction = normalizeSortDirection(sortDir);

        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(direction, normalizedSortBy)
        );

        Specification<Product> spec = Specification.where(null);
        if (name != null && !name.isBlank()) {
            String keyword = "%" + name.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), keyword));
        }
        if (minPrice != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice));
        }
        if (maxPrice != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice));
        }
        if (minStock != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("stock"), minStock));
        }
        if (maxStock != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("stock"), maxStock));
        }

        Page<Product> result = repository.findAll(spec, pageRequest);
        List<ProductResponse> items = result.getContent().stream().map(this::toResponse).toList();

        return new ProductPageResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                normalizedSortBy,
                direction.name(),
                result.hasNext(),
                result.hasPrevious()
        );
    }

    public ProductResponse findById(UUID id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
    }

    // =========================
    // STOCK MANAGEMENT
    // =========================
    @Transactional
    public void checkAndDecreaseStock(UUID productId, int quantity) {
        if (quantity <= 0) {
            throw new BadRequestException("INVALID_QUANTITY", "Quantity must be greater than 0");
        }

        Product product = repository.findById(productId)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        if (product.getStock() < quantity) {
            throw new BadRequestException("INSUFFICIENT_STOCK", "Product not found or insufficient stock");
        }

        product.setStock(product.getStock() - quantity);
        try {
            repository.saveAndFlush(product);
        } catch (OptimisticLockingFailureException ex) {
            throw new ConflictException("STOCK_UPDATE_CONFLICT", "Concurrent stock update detected. Please retry.");
        }
    }

    @Transactional
    public void increaseStock(UUID productId, int quantity) {
        if (quantity <= 0) {
            throw new BadRequestException("INVALID_QUANTITY", "Quantity must be greater than 0");
        }

        Product product = repository.findById(productId)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        product.setStock(product.getStock() + quantity);
        try {
            repository.saveAndFlush(product);
        } catch (OptimisticLockingFailureException ex) {
            throw new ConflictException("STOCK_UPDATE_CONFLICT", "Concurrent stock update detected. Please retry.");
        }
    }

    private void applyFullUpdate(Product product, ProductCreateRequest request, String userRole) {
        validateProductManagerRole(userRole);
        product.setName(normalizeName(request.getName()));
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
    }

    private Product getProductOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
    }

    private void validateProductManagerRole(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            throw new ForbiddenException("MISSING_ROLE", "Missing user role");
        }

        if (!"SELLER".equalsIgnoreCase(userRole) && !"ADMIN".equalsIgnoreCase(userRole)) {
            throw new ForbiddenException("ROLE_NOT_ALLOWED", "Only SELLER or ADMIN can manage product");
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("INVALID_NAME", "name must not be blank");
        }
        return name.trim();
    }

    private void validateRange(Number min, Number max, String fieldName) {
        if (min != null && max != null && min.doubleValue() > max.doubleValue()) {
            throw new BadRequestException(
                    "INVALID_RANGE",
                    "min" + capitalize(fieldName) + " must be less than or equal to max" + capitalize(fieldName)
            );
        }
    }

    private String normalizeSortBy(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "createdAt";
        }
        return switch (sortBy) {
            case "name", "price", "stock", "createdAt" -> sortBy;
            default -> throw new BadRequestException(
                    "INVALID_SORT_FIELD",
                    "sortBy must be one of: name, price, stock, createdAt"
            );
        };
    }

    private Sort.Direction normalizeSortDirection(String sortDir) {
        if (sortDir == null || sortDir.isBlank()) {
            return Sort.Direction.DESC;
        }
        if ("asc".equalsIgnoreCase(sortDir)) {
            return Sort.Direction.ASC;
        }
        if ("desc".equalsIgnoreCase(sortDir)) {
            return Sort.Direction.DESC;
        }
        throw new BadRequestException("INVALID_SORT_DIRECTION", "sortDir must be either asc or desc");
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                product.getCreatedAt()
        );
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
