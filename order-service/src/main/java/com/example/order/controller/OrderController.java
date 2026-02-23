package com.example.order.controller;

import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderSagaStepView;
import com.example.order.dto.OrderWorkflowResponse;
import com.example.order.dto.PendingOutboxEventView;
import com.example.order.model.Order;
import com.example.order.service.OrderService;
import com.example.order.service.OrderWorkflowException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> createOrder(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRoleHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        try {
            UUID authenticatedUserId = parseUserId(userIdHeader);

            OrderService.OrderCreationResult result = service.createOrder(
                    authenticatedUserId,
                    userRoleHeader,
                    idempotencyKey,
                    correlationId,
                    request
            );

            HttpStatus status = result.isIdempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
            OrderWorkflowResponse payload = new OrderWorkflowResponse();
            payload.setIdempotentReplay(result.isIdempotentReplay());
            payload.setOrder(result.getOrder());
            payload.setCorrelationId(result.getCorrelationId());
            payload.setSagaSteps(result.getSagaSteps());

            return ResponseEntity.status(status).body(payload);
        } catch (OrderService.IdempotencyConflictException e) {
            return error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", e.getMessage());
        } catch (SecurityException e) {
            return error(HttpStatus.FORBIDDEN, "FORBIDDEN", e.getMessage());
        } catch (OrderWorkflowException e) {
            return error(HttpStatus.valueOf(e.getStatus()), e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error when creating order", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error");
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
            return error(HttpStatus.FORBIDDEN, "FORBIDDEN", e.getMessage());
        } catch (OrderWorkflowException e) {
            return error(HttpStatus.valueOf(e.getStatus()), e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error when listing orders for requestedUserId={}", requestedUserId, e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error");
        }
    }

    @GetMapping("/{id}/saga")
    public ResponseEntity<?> getSagaSteps(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRoleHeader
    ) {
        try {
            UUID authenticatedUserId = parseUserId(userIdHeader);
            List<OrderSagaStepView> steps = service.listOrderSagaSteps(id, authenticatedUserId, userRoleHeader);
            return ResponseEntity.ok(steps);
        } catch (SecurityException e) {
            return error(HttpStatus.FORBIDDEN, "FORBIDDEN", e.getMessage());
        } catch (OrderWorkflowException e) {
            return error(HttpStatus.valueOf(e.getStatus()), e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error when reading saga steps for orderId={}", id, e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error");
        }
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOrder(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRoleHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        try {
            UUID authenticatedUserId = parseUserId(userIdHeader);
            Order cancelledOrder = service.cancelOrder(id, authenticatedUserId, userRoleHeader, correlationId);
            return ResponseEntity.ok(cancelledOrder);
        } catch (SecurityException e) {
            return error(HttpStatus.FORBIDDEN, "FORBIDDEN", e.getMessage());
        } catch (OrderWorkflowException e) {
            return error(HttpStatus.valueOf(e.getStatus()), e.getCode(), e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error");
        }
    }

    @GetMapping("/outbox/pending")
    public ResponseEntity<?> getPendingOutbox(
            @RequestHeader(value = "X-User-Role", required = false) String userRoleHeader,
            @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        try {
            if (!"ADMIN".equalsIgnoreCase(userRoleHeader)) {
                return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only ADMIN can view pending outbox events");
            }

            List<PendingOutboxEventView> events = service.getPendingOutboxEvents(limit);
            return ResponseEntity.ok(events);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error");
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

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", Map.of(
                "code", code,
                "message", message
        ));
        return ResponseEntity.status(status).body(payload);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid request")
                .orElse("Invalid request");
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }
}
