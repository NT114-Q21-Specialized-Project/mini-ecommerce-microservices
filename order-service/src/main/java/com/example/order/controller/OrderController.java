package com.example.order.controller;

import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.PendingOutboxEventView;
import com.example.order.model.Order;
import com.example.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> createOrder(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRoleHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        try {
            UUID authenticatedUserId = parseUserId(userIdHeader);

            OrderService.OrderCreationResult result = service.createOrder(
                    authenticatedUserId,
                    userRoleHeader,
                    idempotencyKey,
                    request
            );

            HttpStatus status = result.isIdempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("idempotentReplay", result.isIdempotentReplay());
            payload.put("order", result.getOrder());

            return ResponseEntity.status(status).body(payload);
        } catch (OrderService.IdempotencyConflictException e) {
            return error(HttpStatus.CONFLICT, e.getMessage());
        } catch (SecurityException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return error(HttpStatus.BAD_GATEWAY, e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    @GetMapping
    public ResponseEntity<?> listOrders(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRoleHeader,
            @RequestParam(value = "userId", required = false) UUID requestedUserId
    ) {
        try {
            UUID authenticatedUserId = parseUserId(userIdHeader);
            List<Order> orders = service.getOrders(authenticatedUserId, userRoleHeader, requestedUserId);
            return ResponseEntity.ok(orders);
        } catch (SecurityException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOrder(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRoleHeader
    ) {
        try {
            UUID authenticatedUserId = parseUserId(userIdHeader);
            Order cancelledOrder = service.cancelOrder(id, authenticatedUserId, userRoleHeader);
            return ResponseEntity.ok(cancelledOrder);
        } catch (SecurityException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return error(HttpStatus.BAD_GATEWAY, e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    @GetMapping("/outbox/pending")
    public ResponseEntity<?> getPendingOutbox(
            @RequestHeader(value = "X-User-Role", required = false) String userRoleHeader,
            @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        try {
            if (!"ADMIN".equalsIgnoreCase(userRoleHeader)) {
                return error(HttpStatus.FORBIDDEN, "Only ADMIN can view pending outbox events");
            }

            List<PendingOutboxEventView> events = service.getPendingOutboxEvents(limit);
            return ResponseEntity.ok(events);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    private UUID parseUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new SecurityException("Missing X-User-Id header");
        }

        try {
            return UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException e) {
            throw new SecurityException("Invalid X-User-Id header format");
        }
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity
                .status(status)
                .body(Map.of("error", message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid request")
                .orElse("Invalid request");
        return error(HttpStatus.BAD_REQUEST, message);
    }
}
